package elasticapps;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

class VmInstance implements Comparable {
	public long startTime;
	public long billingHourStartTime;
	public long endTime;
	public boolean scheduledToKill;
	public int machineID;

	public VmInstance(int machineID, long startTime, long endTime, boolean scheduledToKill) {
		this.machineID = machineID;
		this.startTime = startTime;
		this.endTime = endTime;
		this.scheduledToKill = scheduledToKill;
		this.billingHourStartTime = this.startTime;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("Machine ID: ").append(machineID).append("Start Time: ").append(startTime).append("End Time: ")
				.append(endTime);
		return builder.toString();
	}

	@Override
	public int compareTo(Object o) {
		// TODO Auto-generated method stub
		long startOfComp = ((VmInstance) o).startTime;
		return (int) (this.startTime - startOfComp);
	}
}

public class elasticapp {

	static final String USER_ARRIVAL_LOG = "/Users/subramanya/Documents/workspace/autoscaling/logs/created_dataset.csv";
	static final String CSV_DELIMITOR = ",";
	static long startTimeStamp;
	static ArrayList<VmInstance> runningInstance = new ArrayList<>();
	static int machineID = 0;
	static int numberOfUserPerInstance = 1000;

	public static enum ScalingType {
		MORE, LESS
	};

	public static void main(String[] args) throws IOException, InterruptedException {
		String line = "";
		BufferedReader br;
		InfluxDB influxDB = InfluxDBFactory.connect("http://80.156.222.17:8086", "root", "root");
		String dbName = "scalingDecision";
		influxDB.createDatabase(dbName);

		try {
			br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));
			for (int i = 0; i < 43200; i++) {
				line = br.readLine();
				String[] dataRow = line.split(CSV_DELIMITOR);
				long timeStampT = Long.parseLong(dataRow[0]);
				int totalRequestAtT = Integer.parseInt(dataRow[1]);
				if (i == 0) {
					startTimeStamp = timeStampT;
				}
				System.out.printf("Time:%d Users:%d \n", timeStampT, totalRequestAtT);
				scalingDecision(timeStampT, totalRequestAtT);
				killVmEndingBillingPeriod(timeStampT);

				Point point1 = Point.measurement("numberofvms").time(timeStampT, TimeUnit.SECONDS)
						.field("vms", runningInstance.size()).build();
				influxDB.write(dbName, "default", point1);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void killVmEndingBillingPeriod(long timeStamp) {
		Collections.sort(runningInstance);
		// Check for the load between 60 to 65 mins.
		int nosVmToKill = 0;

		// else kill.
		for (int i = 0; i < runningInstance.size(); i++) {
			VmInstance instance = runningInstance.get(i);
			long endStart = instance.billingHourStartTime + (60 * 50);
			if (timeStamp >= endStart && endStart <= instance.endTime) {
				// Calculate how many machines do we need for next billing
				// period.
				// If we need the machines which is nearing billing period: then
				// no kill.
				ArrayList<Integer> userInInterval5 = getRequestCountsInTimeRange(instance.endTime,
						instance.endTime + (60 * 15));
				int maxUserInterval5 = userInInterval5.get(userInInterval5.size() - 1);
				int newMachineReq = (int) Math.round(maxUserInterval5 / numberOfUserPerInstance) + 1;
				int machineRunning = runningInstance.size();
				if (newMachineReq > machineRunning) {
					runningInstance.get(i).billingHourStartTime = runningInstance.get(i).endTime;
					runningInstance.get(i).endTime = runningInstance.get(i).endTime + (60 * 60);
				} else {
					runningInstance.get(i).scheduledToKill = true;
					runningInstance.remove(i);
					nosVmToKill += 1;
				}
				for (int j = 0; j < runningInstance.size(); j++) {
					if (runningInstance.get(j).scheduledToKill && (runningInstance.get(j).endTime == timeStamp)) {
						runningInstance.remove(j);
					}
				}
			}
		}
		System.out.printf("At T: %d Nos VM killed: %d \n", timeStamp, nosVmToKill);
	}

	private static void scalingDecision(long timeStamp, int totalPredictedRequest) {
		// Get predicted load.
		// Check for scale up or down.
		if (timeStamp == startTimeStamp) {
			int machineReq = (int) Math.round(totalPredictedRequest / numberOfUserPerInstance) + 1;
			for (int i = 0; i < machineReq; i++) {
				runningInstance.add(new VmInstance(++machineID, startTimeStamp, startTimeStamp + (60 * 60), false));
			}

		}
		// if scale up - how many on demond vm to use.
		ArrayList<Integer> userInInterval5 = getRequestCountsInTimeRange(timeStamp, timeStamp + (60 * 5));
		int maxUserInterval5 = userInInterval5.get(userInInterval5.size() - 1);
		int newMachineReq = (int) Math.round(maxUserInterval5 / numberOfUserPerInstance) + 1;
		int machineRunning = runningInstance.size();
		if (newMachineReq > machineRunning) {
			int machinesToAdd = newMachineReq - machineRunning;
			for (int i = 0; i < machinesToAdd; i++) {
				runningInstance.add(new VmInstance(++machineID, startTimeStamp, startTimeStamp + (60 * 60), false));
			}

		} else {
			// No scale to be done.
			// Check if some vm are coming to billing period end.
		}
		// Output to graph database.
		System.out.printf("At T: %d Nos VM Running: %d \n", timeStamp, runningInstance.size());
	}

	private static ArrayList<Integer> getRequestCountsInTimeRange(long startT, long endT) {
		String line = "";
		ArrayList<Integer> countArray = new ArrayList<>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));
			while ((line = br.readLine()) != null) {
				String[] dataRow = line.split(CSV_DELIMITOR);
				long timeStampT = Long.parseLong(dataRow[0]);
				int totalRequestAtT = Integer.parseInt(dataRow[1]);
				if (timeStampT >= startT && timeStampT <= endT) {
					countArray.add(totalRequestAtT);
				}
				if (timeStampT > endT)
					break;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Collections.sort(countArray);
		return countArray;
	}
}
