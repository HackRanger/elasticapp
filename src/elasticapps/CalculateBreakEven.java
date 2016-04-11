package elasticapps;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class CalculateBreakEven {
	static final String USER_ARRIVAL_LOG = "/Users/subramanya/Documents/workspace/autoscaling/logs/hourofmonthvmsreq.csv";

	public static void main(String[] args) throws IOException {
		BufferedReader br;
		br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));
		ArrayList<Long> hourlyVMPerMonth = new ArrayList<>();
		for (int i = 0; i < 720; i++) {
			String line = br.readLine();
			hourlyVMPerMonth.add(Math.round(Double.parseDouble(line.trim())));
		}
		Collections.sort(hourlyVMPerMonth);
		Set<Long> uniq = new TreeSet<Long>(hourlyVMPerMonth);

		for (Long long1 : uniq) {
			long myval = long1;
			double freq = Collections.frequency(hourlyVMPerMonth, long1);
			double perc = (freq / 720) * 100;
			double costOD = myval * freq * 0.8;
			double costRI = myval * freq * 0.6;
			double total = costOD + costRI;
			System.out.println("No of Instance of: " + myval + " Freq: " + freq + " Freq in per:" + perc + "%"
					+ " Cost of OD: " + costOD + " Cost of RI: " + costRI + " Total= :" + total);
		}
	}

}
