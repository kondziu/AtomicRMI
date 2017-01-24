package put.atomicrmi.agents;

import put.atomicrmi.optsva.RollbackForcedException;
import put.atomicrmi.optsva.Transaction;

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
public class Eval {
    private static final int INF = Transaction.INF;
    private final Registry registry;

    public Eval() throws RemoteException {
        registry = LocateRegistry.createRegistry(9001);
    }

    public <T> void initializeBelief(String id, T value, boolean initiallyTrue) throws RemoteException {
        registry.rebind(id, new BeliefImpl<T>(value, initiallyTrue));
    }

    public <T> void initializeGoal(String id, T value, boolean initiallyTrue) throws RemoteException {
        registry.rebind(id, new GoalImpl<T>(value, initiallyTrue));
    }

    public <T> T getFromRegistry(String id) throws RemoteException, NotBoundException {
        // todo allow multiple registries
        return (T) registry.lookup(id);
    }

    public <T> List<T> getFromRegistry(String[] id) throws RemoteException, NotBoundException {
        ArrayList<T> objects = new ArrayList<T>();
        // todo allow multiple registries
        for (int i = 0; i < id.length; i++) {
            objects.add((T) registry.lookup(id[i]));
        }
        return objects;
    }

    public void evaluatePlan(Agent agent, String plan) throws RemoteException, NotBoundException, InvocationTargetException, IllegalAccessException {
        Method[] methods = agent.getClass().getDeclaredMethods();
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
        Set<String> contextIDSet = new HashSet(Arrays.asList());
        List<Belief> contextBeliefs = getFromRegistry(contextIDs);

        /* Create transaction preamble. */
        Transaction transaction = new Transaction();

        /* Create preamble for goals. */
        Execution execution = method.getAnnotation(Execution.class);
        AccessGoal[] accessGoals = execution.goals();
        Goal[] goals = new Goal[accessGoals.length];
        for (int i = 0 ; i < accessGoals.length; i++) {
            AccessGoal g = accessGoals[0];
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
            AccessBelief b = accessBeliefs[0];
            int contextIndex = this.indexOf(contextIDs, b.belief());
            if (contextIndex >= 0) { // assume extra read op
                if (b.writes() == 0) {
                    beliefs[i] = transaction.reads(contextBeliefs.get(contextIndex),
                            b.reads() == INF ? INF : b.reads() + 1);
                } else {
                    beliefs[i] = transaction.reads(contextBeliefs.get(contextIndex),
                            b.reads() == INF || b.writes() == INF ? INF : b.reads() + b.writes() + 1);
                }
            } else {
                Belief belief = getFromRegistry(b.belief());
                if (b.writes() == 0) {
                    beliefs[i] = transaction.reads(belief, b.reads());
                } else if (b.reads() == 0) {
                    beliefs[i] = transaction.writes(belief, b.writes());
                } else {
                    beliefs[i] = transaction.accesses(belief,
                            b.reads() == INF || b.writes() == INF ? INF : b.reads() + b.writes(),
                            b.reads(), b.writes());
                }
            }
        }

        try {
            transaction.start();

            /* Check context. */
            if (!checkBeliefs(contextBeliefs)) {
                transaction.rollback();
                return null;
            }

            /* Prepare method for execution. */
            Object[] arguments = new Object[beliefs.length + goals.length];
            for (int i = 0; i < beliefs.length; i++) {
                arguments[i] = beliefs[i];
            }
            for (int i = 0; i < goals.length; i++) {
                arguments[i + beliefs.length] = goals[i];
            }

            Object returnValue = method.invoke(agent, arguments);

            transaction.commit();
            return returnValue;
            
        } catch (RollbackForcedException e) {
            return null;
        }

    }

    private boolean checkBeliefs(List<Belief> contextBeliefs) throws RemoteException {
        for (Belief belief : contextBeliefs) {
            if(!belief.isTrue())
                return false;
        }
        return true;
    }

    int consumingAdd (int x, int y) {
        if (x == INF || y == INF) {
            return INF;
        }
        return x + y;
    }

    private int indexOf(String[] context, String belief) {
        for (int i = 0; i < belief.length(); i++) {
            if (context[i].equals(belief))
                return i;
        }
        return -1;
    }

    public static void main(String[] args) throws RemoteException, NotBoundException, InvocationTargetException, IllegalAccessException {
        Eval agentSystem = new Eval();
        agentSystem.initializeBelief("X", 0, true);
        agentSystem.initializeBelief("Y", 0, true);
        agentSystem.initializeBelief("Z", 0, true);
        agentSystem.initializeGoal("G1", 0, true);


        Agent agent = new Agent();

        agentSystem.evaluatePlan(agent, "plan");
    }
}
