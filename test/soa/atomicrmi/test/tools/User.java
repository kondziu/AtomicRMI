package soa.atomicrmi.test.tools;

import java.util.InputMismatchException;
import java.util.Scanner;

import soa.atomicrmi.RollbackForcedException;
import soa.atomicrmi.Transaction;
import soa.atomicrmi.TransactionException;

public class User {
	public enum Choice {
		COMMIT, ROLLBACK, RETRY
	}

	public static final void end(Transaction transaction) throws TransactionException, RollbackForcedException {
		Choice choice = User.selectEnding();
		switch (choice) {
		case COMMIT:
			System.out.println("Committing.");
			transaction.commit();
			break;
		case ROLLBACK:
			System.out.println("Rolling back.");
			transaction.rollback();
			break;
		}
	}

	public static final Choice selectEnding() {
		return selectAnyEnding(false);
	}

	public static final Choice selectAnyEnding() {
		return selectAnyEnding(true);
	}

	public static final void waitUp() {
		try {
			System.out.println("Press a key to continue.");
			System.in.read();
		} catch (Exception e) {
			// Ignore.
		}
	}

	private static final Choice selectAnyEnding(boolean includeRetry) {
		Choice ending = null;
		while (ending == null) {
			System.out.println("Alternative endings:");
			System.out.println("\t1. commit");
			System.out.println("\t2. rollback");
			if (includeRetry) {
				System.out.println("\t3. retry");
			}
			System.out.print("Pick an ending: ");
			try {
				Scanner scanner = new Scanner(System.in);
				int choices = includeRetry ? 3 : 2;
				int choice = scanner.nextInt();
				if (choice <= choices && choice >= 1) {
					ending = Choice.values()[choice - 1];
				}
			} catch (InputMismatchException e) {
				// Ignore.
			}
		}
		return ending;
	}
}
