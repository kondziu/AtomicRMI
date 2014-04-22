package put.atomicrmi.test.distributed;

import java.io.IOException;
import java.util.InputMismatchException;
import java.util.Scanner;

public class Tools {
	enum Ending {
		COMMIT, ROLLBACK, RETRY
	}

	public static final Ending pickEnding() throws IOException {
		System.out.println("Alternative endings:");
		System.out.println("\t1. commit");
		System.out.println("\t2. rollback");
		System.out.println("\t3. retry");
		System.out.print("Pick an ending: ");

		Scanner scanner = new Scanner(System.in);
		try {
			int choice = scanner.nextInt();

			switch (choice) {
			case 1:
			case 2:
			case 3:
				return Ending.values()[choice - 1];
			default:
				return null;
			}

		} catch (InputMismatchException e) {
			return null;
		} finally {
			scanner.close();
		}
	}
}
