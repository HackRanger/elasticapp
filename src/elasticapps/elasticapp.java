package elasticapps;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

class VmInstance implements Comparable {
	public long startTime;
	public long billingHourStartTime;
	public long billingHourEndTime;
	public long endTime;
	public long startOfActivePeriod;
	public long endOfActivePeriod;
	public boolean scheduledToKill;
	public int machineID = 0;

	public VmInstance(int machineID, long billingHourStartTime, long billingHourEndTime, long startOfActivePeriod,
			long endOfActivePeriod, boolean scheduledToKill) {
		this.machineID = machineID;
		this.startTime = billingHourStartTime;
		this.endTime = billingHourEndTime;
		this.billingHourStartTime = billingHourStartTime;
		this.billingHourEndTime = billingHourEndTime;
		this.startOfActivePeriod = startOfActivePeriod;
		this.endOfActivePeriod = endOfActivePeriod;
		this.scheduledToKill = scheduledToKill;
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
		long startOfComp = ((VmInstance) o).billingHourStartTime;
		return (int) (this.billingHourStartTime - startOfComp);
	}
}

public class elasticapp {

	static final String USER_ARRIVAL_LOG = "/Users/subramanya/Documents/workspace/autoscaling/logs/created_dataset.csv";
	static final String CSV_DELIMITOR = ",";
	static long startTimeStamp;
	static ArrayList<VmInstance> runningInstance = new ArrayList<>();
	static int machineID = 0;
	static int numberOfUserPerInstance = 1000;

	static int secondInMin = 60;
	static int timeTakenToActive = 5 * secondInMin;
	static int timetakenToShutdown = 10 * secondInMin;
	static int scaleUpLookAhead = 5 * secondInMin;
	// scaleDownLookAhead starting from endOfBillingPeriod
	static int scaleDownLookAhead = 15 * secondInMin;
	static int billingPeriod = 60 * secondInMin;

	public static enum ScalingType {
		MORE, LESS
	};

	public static void main(String[] args) throws IOException, InterruptedException {
		String line = "";
		BufferedReader br;
		InfluxDB influxDB = InfluxDBFactory.connect("http://80.156.222.17:8086", "root", "root");
		String dbName = "scalingDecision";
		influxDB.createDatabase(dbName);
		long timeStampT = 0;
		String[] dataRow;
		try {
			br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));
			for (int i = 0; i < 1440 * 20; i++) {
				line = br.readLine();
				dataRow = line.split(CSV_DELIMITOR);
				timeStampT = Long.parseLong(dataRow[0]);
				int totalRequestAtT = Integer.parseInt(dataRow[1]);
				if (i == 0) {
					startTimeStamp = timeStampT;
				}
				// System.out.printf("Time:%d Users:%d \n", timeStampT,
				// totalRequestAtT);
				scalingDecision(timeStampT, totalRequestAtT);
				killVmEndingBillingPeriod(timeStampT);

				Point point1 = Point.measurement("vmswithoutsmartkill").time(timeStampT, TimeUnit.SECONDS)
						.field("vms", totalRequestAtT / numberOfUserPerInstance).build();
				influxDB.write(dbName, "default", point1);

				ArrayList<VmInstance> billingInstance = new ArrayList<>();

				for (int j = 0; j < runningInstance.size(); j++) {
					if (timeStampT >= runningInstance.get(j).billingHourStartTime
							&& timeStampT <= runningInstance.get(j).billingHourEndTime)
						billingInstance.add(runningInstance.get(j));
				}

				ArrayList<VmInstance> activeInstance = new ArrayList<>();

				for (int j = 0; j < runningInstance.size(); j++) {
					if (timeStampT >= runningInstance.get(j).startOfActivePeriod
							&& timeStampT <= runningInstance.get(j).endOfActivePeriod)
						activeInstance.add(runningInstance.get(j));
				}

				Point point2 = Point.measurement("numberofvmsbilling").time(timeStampT, TimeUnit.SECONDS)
						.field("vms", billingInstance.size()).build();
				influxDB.write(dbName, "default", point2);

				Point point3 = Point.measurement("numberofvmsactive").time(timeStampT, TimeUnit.SECONDS)
						.field("vms", activeInstance.size()).build();
				influxDB.write(dbName, "default", point3);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void killVmEndingBillingPeriod(long timeStamp) {
		Collections.sort(runningInstance);
		// Check for the load between 50 to 65 mins.
		for (int i = 0; i < runningInstance.size(); i++) {
			if (timeStamp >= runningInstance.get(i).endOfActivePeriod) {
				// Calculate how many machines do we need for next billing
				// period.
				// If we need the machines which is nearing billing period: then
				// no kill.
				ArrayList<Integer> usersInInterval = getRequestCountsInTimeRange(
						runningInstance.get(i).endOfActivePeriod,
						runningInstance.get(i).billingHourEndTime + (scaleDownLookAhead));
				int maxUserInterval = usersInInterval.get(usersInInterval.size() - 1);
				int newMachineReq = (int) Math.round(maxUserInterval / numberOfUserPerInstance) + 1;
				int machineRunning = runningInstance.size();
				if (newMachineReq > machineRunning) {
					// Renew machines
					runningInstance.get(i).billingHourStartTime = runningInstance.get(i).billingHourStartTime
							+ billingPeriod;
					runningInstance.get(i).billingHourEndTime = runningInstance.get(i).billingHourStartTime
							+ billingPeriod;
					runningInstance.get(i).startOfActivePeriod = runningInstance.get(i).billingHourStartTime
							+ timeTakenToActive;
					runningInstance.get(i).endOfActivePeriod = runningInstance.get(i).billingHourEndTime
							- timetakenToShutdown;
				} else {
					runningInstance.get(i).scheduledToKill = true;
				}
			}
			for (int j = 0; j < runningInstance.size(); j++) {
				if (runningInstance.get(j).scheduledToKill
						&& (runningInstance.get(j).billingHourEndTime == timeStamp)) {
					System.out.printf("Machines with ID: %d Started at: %s Ended at: %s Used for Mins: %d\n",
							runningInstance.get(j).machineID, new Date(runningInstance.get(j).startTime * 1000),
							new Date(runningInstance.get(j).billingHourEndTime * 1000),
							(runningInstance.get(j).billingHourEndTime - runningInstance.get(j).startTime)
									/ secondInMin);
					runningInstance.remove(j);
				}
			}
		}
	}

	private static void scalingDecision(long timeStamp, int totalPredictedRequest) {
		// Get predicted load.
		// Check for scale up or down.

		ArrayList<Integer> userInInterval5 = getRequestCountsInTimeRange(timeStamp + timeTakenToActive,
				timeStamp + timeTakenToActive + scaleUpLookAhead);
		int maxUserInterval5 = userInInterval5.get(userInInterval5.size() - 1);
		int newMachineReq = (int) Math.round(maxUserInterval5 / numberOfUserPerInstance) + 1;
		if (timeStamp == startTimeStamp) {
			for (int i = 0; i < newMachineReq; i++) {
				// int machineID, long billingHourStartTime, long
				// billingHourEndTime, long startOfActivePeriod,
				// long endOfActivePeriod, boolean scheduledToKill
				runningInstance.add(new VmInstance(++machineID, timeStamp, timeStamp + billingPeriod,
						timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown, false));
			}
			return;
		}
		// if scale up - how many on demond vm to use.

		int machineRunning = runningInstance.size();
		if (newMachineReq > machineRunning) {
			int machinesToAdd = newMachineReq - machineRunning;
			for (int i = 0; i < machinesToAdd; i++) {
				runningInstance.add(new VmInstance(++machineID, timeStamp, timeStamp + billingPeriod,
						timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown, false));
			}

		} else {
			// No scale to be done.
			// Check if some vm are coming to billing period end.
		}
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
