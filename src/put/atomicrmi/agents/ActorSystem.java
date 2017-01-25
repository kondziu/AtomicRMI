package put.atomicrmi.agents;

import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.Transaction;
import put.util.Pair;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.*;

/**
 * Created by ksiek on 24.01.17.
 */
public class ActorSystem {
    private static final int INF = Transaction.INF;
    private final Registry registry;

    public ActorSystem() throws RemoteException {
        registry = LocateRegistry.createRegistry(9001);
    }

    public <T> void registerBelief(String id, T value, boolean initiallyTrue) throws RemoteException {
        registry.rebind(id, new BeliefImpl<>(value, initiallyTrue));
    }

    public <T> void registerGoal(String id, T value, boolean initiallyTrue) throws RemoteException {
        registry.rebind(id, new GoalImpl<>(value, initiallyTrue));
    }

    public <T> T getFromRegistry(String id) throws RemoteException, NotBoundException {
        // todo allow multiple registries
        return (T) registry.lookup(id);
    }

    public <T> List<T> getFromRegistry(String[] id) throws RemoteException, NotBoundException {
        ArrayList<T> objects = new ArrayList<>();
        // todo allow multiple registries
        for (int i = 0; i < id.length; i++)
            objects.add((T) registry.lookup(id[i]));
        return objects;
    }

    public void evaluatePlan(Agent agent, String plan) throws RemoteException, NotBoundException, InvocationTargetException, IllegalAccessException {
        Method[] methods = agent.getClass().getMethods();
        for (Method method : methods) {
            if (method.getName() != plan)
                continue;
            evaluatePlan(agent, method);
            return;
        }
        throw new RuntimeException("Plan " + plan + " not found in agent class " + agent.getClass().getCanonicalName() + ".");
    }

    public Object evaluatePlan(Agent agent, Method method) throws RemoteException, NotBoundException, InvocationTargetException, IllegalAccessException {

        /* Obtain metadata. */
        Context context = method.getAnnotation(Context.class);
        String[] contextIDs = context.value();
        List<Belief> contextBeliefs = getFromRegistry(contextIDs);

        /* Create transaction preamble. */
        Transaction transaction = new Transaction();

        /* Create preamble for goals. */
        Execution execution = method.getAnnotation(Execution.class);
        AccessGoal[] accessGoals = execution.goals();
        Goal[] goals = new Goal[accessGoals.length];
        for (int i = 0 ; i < accessGoals.length; i++) {
            AccessGoal g = accessGoals[i];
            Goal goal = getFromRegistry(g.goal());
            if (g.writes() == 0) {
                goals[i] = transaction.reads(goal, g.reads());
            } else if (g.reads() == 0) {
                goals[i] = transaction.writes(goal, g.writes());
            } else {
                goals[i] = transaction.accesses(goal,
                        g.reads() == INF || g.writes() == INF ? INF : g.reads() + g.writes(),
                        g.reads(), g.writes());
            }
        }

        /* Create preamble for beliefs. */
        AccessBelief[] accessBeliefs = execution.beliefs();
        Belief[] beliefs = new Belief[accessBeliefs.length];
        for (int i = 0; i < accessBeliefs.length; i++) {
            AccessBelief b = accessBeliefs[i];
            int contextIndex = this.indexOf(contextIDs, b.belief());
            if (contextIndex >= 0) { // assume extra read op
                if (b.writes() == 0) {
                    System.out.println("(A) " + i + ": " + b.belief() + " -> " + b.writes() + " " + b.reads());
                    beliefs[i] = transaction.reads(contextBeliefs.get(contextIndex),
                            b.reads() == INF ? INF : b.reads() + 1);
                } else {
                    System.out.println("(B) " + i + ": " + b.belief() + " -> " + b.writes() + " " + b.reads());
                    beliefs[i] = transaction.accesses(contextBeliefs.get(contextIndex),
                            b.reads() == INF || b.writes() == INF ? INF : b.reads() + b.writes() + 1,
                            b.reads(), b.writes());
                }
            } else {
                Belief belief = getFromRegistry(b.belief());
                if (b.writes() == 0) {
                    System.out.println("(C) " + i + ": " + b.belief() + " -> " + b.writes() + " " + b.reads());
                    beliefs[i] = transaction.reads(belief, b.reads());
                } else if (b.reads() == 0) {
                    System.out.println("(D) " + i + ": " + b.belief() + " -> " + b.writes() + " " + b.reads());
                    beliefs[i] = transaction.writes(belief, b.writes());
                } else {
                    System.out.println("(E) " + i + ": " + b.belief() + " -> " + b.writes() + " " + b.reads());
                    beliefs[i] = transaction.accesses(belief,
                            b.reads() == INF || b.writes() == INF ? INF : b.reads() + b.writes(),
                            b.reads(), b.writes());
                }
            }
        }

        try {
            transaction.start();
            System.out.println("Starting transaction for " + agent + " -> " + method.getName());

            /* Check context. */
            if (!checkBeliefs(contextBeliefs)) {
                System.out.println("Rolling back transaction for " + agent + " -> " + method.getName());
                transaction.rollback();
                return null;
            }

            /* Prepare method for execution. */
            Object[] arguments = new Object[beliefs.length + goals.length];
            for (int i = 0; i < beliefs.length; i++)
                arguments[i] = beliefs[i];

            for (int i = 0; i < goals.length; i++)
                arguments[i + beliefs.length] = goals[i];

            /* Execte method. */
            Object returnValue = method.invoke(agent, arguments);

            System.out.println("Committing transaction for " + agent + " -> " + method.getName());
            transaction.commit();
            return returnValue;
            
        } catch (RollbackForcedException e) {
            System.out.println("Forced abort on transaction for " + agent + " -> " + method.getName());
            return null;
        }

    }

    private boolean checkBeliefs(List<Belief> contextBeliefs) throws RemoteException {
        for (Belief belief : contextBeliefs)
            if(!belief.isTrue())
                return false;
        return true;
    }

    private int indexOf(String[] context, String belief) {
        for (int i = 0; i < belief.length(); i++)
            if (context[i].equals(belief))
                return i;
        return -1;
    }

    private Map<Pair<Agent.Trigger, String>, Set<Pair<Agent, Method>>> registeredPlans = new HashMap<>();

    public void register(Agent agent) {
        Method[] methods = agent.getClass().getMethods();
        List<Method> plans = new ArrayList<>();
        for (Method method : methods) {
            Triggers triggers = method.getAnnotation(Triggers.class);
            if (triggers != null) {
                Event[] triggeringEvents = triggers.value();
                for (Event event : triggeringEvents) {
                    Set<Pair<Agent, Method>> registeredMethods = registeredPlans.get(new Pair<>(event.type(), event.term()));
                    if (registeredMethods == null) {
                        registeredMethods = new HashSet<>();
                        registeredPlans.put(new Pair<>(event.type(), event.term()), registeredMethods);
                    }
                    registeredMethods.add(new Pair<>(agent, method));
                    System.out.println(agent + " -> " + method.getName() + " registered as a plan for trigger (" + event.term() + ", " + event.type() + ")");
                }
            } else {
                System.out.println(method.getName() + " is not a plan.");
            }
        }
    }

    public void trigger(Agent.Trigger trigger, String term) throws RemoteException, InvocationTargetException, IllegalAccessException, NotBoundException {
        Set<Pair<Agent, Method>> plans = registeredPlans.get(new Pair<>(trigger, term));
        System.out.println(plans);
        for (Pair<Agent, Method> pair : plans) {
            final Agent agent = pair.left;
            final Method method = pair.right;
            new Thread() {
                @Override
                public void run() {
                    try {
                        evaluatePlan(agent, method);
                    } catch(Exception e) {
                        throw new RuntimeException(e.getMessage(), e.getCause());
                    }
                }
            }.start();
        }
    }


    public static void main(String[] args) throws RemoteException, NotBoundException, InvocationTargetException, IllegalAccessException {
        /* Create agent system. */
        ActorSystem agentSystem = new ActorSystem();

        /* Initialize universe. */
        agentSystem.registerBelief("X", 0, true);
        agentSystem.registerBelief("Y", 0, true);
        agentSystem.registerBelief("Z", 0, true);
        agentSystem.registerGoal("G1", 0, true);

        /* Initialize agents. */
        Agent agent1 = new AgentCarter();
        Agent agent2 = new AgentSmith();

        /* Register plans and their triggers. */
        agentSystem.register(agent1);
        agentSystem.register(agent2);

        /*************************/
        //agentSystem.evaluatePlan(agent, "plan");
        agentSystem.trigger(Agent.Trigger.ADD_GOAL, "G1");

        System.out.println("done");
    }
}
