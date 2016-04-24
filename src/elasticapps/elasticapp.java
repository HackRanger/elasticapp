package elasticapps;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
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
	public int machineID = 0;

	public VmInstance(int machineID, long billingHourStartTime, long billingHourEndTime, long startOfActivePeriod,
			long endOfActivePeriod) {
		this.machineID = machineID;
		this.startTime = billingHourStartTime;
		this.endTime = billingHourEndTime;
		this.billingHourStartTime = billingHourStartTime;
		this.billingHourEndTime = billingHourEndTime;
		this.startOfActivePeriod = startOfActivePeriod;
		this.endOfActivePeriod = endOfActivePeriod;
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
		long startOfComp = ((VmInstance) o).billingHourEndTime;
		return (int) (this.billingHourEndTime - startOfComp);
	}
}

public class elasticapp {

	static final String USER_ARRIVAL_LOG = "/Users/subramanya/Documents/workspace/autoscaling/logs/rtc_created_dataset.csv";

	static final String USER_DEMAND_DATA_SET = "/Users/subramanya/Documents/workspace/autoscaling/logs/demand_available_billing_dataset.csv";

	static final String VM_BILLING_DATA_SET = "/Users/subramanya/Documents/workspace/autoscaling/logs/vms_billing_hours.csv";

	static final String CSV_DELIMITOR = ",";
	static long startTimeStamp;
	static ArrayList<VmInstance> runningInstance = new ArrayList<>();
	static int machineID = 0;
	static int numberOfUserPerInstance = 120;

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
		BufferedWriter bw;
		InfluxDB influxDB = InfluxDBFactory.connect("http://80.156.222.17:8086", "root", "root");
		String dbName = "scalingDecision";
		influxDB.createDatabase(dbName);
		long timeStampT = 0;
		String[] dataRow;
		try {
			br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));
			bw = new BufferedWriter(new FileWriter(USER_DEMAND_DATA_SET));
			for (int i = 0; i < 4981; i++) {
				line = br.readLine();
				dataRow = line.split(CSV_DELIMITOR);
				timeStampT = Long.parseLong(dataRow[0]);
				int totalRequestAtT = Integer.parseInt(dataRow[1]);
				// if (timeStampT >= 1432883520) {
				if (i == 0) {
					startTimeStamp = timeStampT;
				}
				scalingDecision(timeStampT);

				int coutOfBilling = 0;
				for (int j = 0; j < runningInstance.size(); j++) {
					if (timeStampT >= runningInstance.get(j).billingHourStartTime
							&& timeStampT <= runningInstance.get(j).billingHourEndTime)
						coutOfBilling++;
				}

				int coutOfActive = 0;
				for (int j = 0; j < runningInstance.size(); j++) {
					if (timeStampT >= runningInstance.get(j).startOfActivePeriod
							&& timeStampT <= runningInstance.get(j).endOfActivePeriod)
						coutOfActive++;
				}

				Point point1 = Point.measurement("vms").time(timeStampT, TimeUnit.SECONDS)
						.field("vmswithoutsmartkill", ((int) (totalRequestAtT / numberOfUserPerInstance) + 1)).build();
				influxDB.write(dbName, "default", point1);

				Point point2 = Point.measurement("vms").time(timeStampT, TimeUnit.SECONDS)
						.field("vmswithsmartkill", runningInstance.size()).build();
				influxDB.write(dbName, "default", point2);

				Point point3 = Point.measurement("vms").time(timeStampT, TimeUnit.SECONDS)
						.field("vmsactive", coutOfActive).build();
				influxDB.write(dbName, "default", point3);

				Point point4 = Point.measurement("vms").time(timeStampT, TimeUnit.SECONDS)
						.field("vmbilling", coutOfBilling).build();
				influxDB.write(dbName, "default", point4);

				System.out.println("Time: " + new Date(timeStampT * 1000) + " Users: " + totalRequestAtT + " Demand: "
						+ ((int) (totalRequestAtT / numberOfUserPerInstance) + 1) + " Active: " + coutOfActive
						+ " Billing: " + coutOfBilling);
				bw.write(timeStampT + "," + new Date(timeStampT * 1000) + "," + totalRequestAtT + ","
						+ ((int) (totalRequestAtT / numberOfUserPerInstance) + 1) + "," + coutOfActive + ","
						+ coutOfBilling);
				bw.newLine();
			}

			bw.close();
			BufferedWriter bw1 = new BufferedWriter(new FileWriter(VM_BILLING_DATA_SET, true));
			for (int i = 0; i < runningInstance.size(); i++) {
				// System.out.printf("Machine killed:%d \n",
				// runningInstance.get(i).machineID);
				StringBuilder sb = new StringBuilder().append("VM-ID").append(runningInstance.get(i).machineID)
						.append(" ").append("Start Time: ")
						.append(new Date(runningInstance.get(i).billingHourStartTime * 1000)).append(" ")
						.append("End Time: ").append(new Date(runningInstance.get(i).billingHourEndTime * 1000))
						.append(" ").append("Minutes Used: ").append((runningInstance.get(i).billingHourEndTime
								- runningInstance.get(i).billingHourStartTime) / 60);
				bw1.write(sb.toString());
				bw1.newLine();
				bw1.flush();
				runningInstance.remove(i);
			}

			bw1.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void scalingDecision(long timeStamp) throws IOException {
		// Get predicted load.
		// Check for scale up or down.
		ArrayList<Integer> userInInterval5 = getRequestCountsInTimeRange(timeStamp, timeStamp + timeTakenToActive);
		int maxUserInterval5 = userInInterval5.get(userInInterval5.size() - 1);
		int newMachineReq = (int) Math.round(maxUserInterval5 / numberOfUserPerInstance) + 1;

		if (timeStamp == startTimeStamp) {
			for (int i = 0; i < newMachineReq; i++) {
				// long billingHourStartTime, long billingHourEndTime, long
				// startOfActivePeriod,
				// long endOfActivePeriod
				runningInstance.add(new VmInstance(++machineID, timeStamp, timeStamp + billingPeriod,
						timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));
			}
			return;
		}

		// Machines currently being billed
		int machineRunning = runningInstance.size();

		if (newMachineReq > machineRunning) {
			int machinesToAdd = newMachineReq - machineRunning;
			for (int i = 0; i < machinesToAdd; i++) {
				// long billingHourStartTime, long billingHourEndTime, long
				// startOfActivePeriod,
				// long endOfActivePeriod
				runningInstance.add(new VmInstance(++machineID, timeStamp, timeStamp + billingPeriod,
						timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));
			}
		}

		ArrayList<Integer> usersInInterval15 = getRequestCountsInTimeRange(timeStamp, timeStamp + scaleDownLookAhead);
		int maxUserInterval15 = usersInInterval15.get(usersInInterval15.size() - 1);
		int newMachineReq15 = maxUserInterval15 / numberOfUserPerInstance;

		BufferedWriter bw = new BufferedWriter(new FileWriter(VM_BILLING_DATA_SET, true));

		if (machineRunning > newMachineReq15) {
			ArrayList<VmInstance> bill_stop = new ArrayList<>();
			for (int i = 0; i < runningInstance.size(); i++) {
				if (runningInstance.get(i).endOfActivePeriod == timeStamp)
					bill_stop.add(runningInstance.get(i));
			}
			if (bill_stop.size() > 0) {
				if (machineRunning - newMachineReq15 > bill_stop.size()) {
					for (int j = 0; j < bill_stop.size(); j++) {
						for (int i = 0; i < runningInstance.size(); i++) {
							if (runningInstance.get(i).machineID == bill_stop.get(j).machineID) {
								// System.out.printf("Machine killed:%d \n",
								// runningInstance.get(i).machineID);

								StringBuilder sb = new StringBuilder().append("VM-ID")
										.append(runningInstance.get(i).machineID).append(" ").append("Start Time: ")
										.append(new Date(runningInstance.get(i).billingHourStartTime * 1000))
										.append(" ").append("End Time: ")
										.append(new Date(runningInstance.get(i).billingHourEndTime * 1000)).append(" ")
										.append("Minutes Used: ").append((runningInstance.get(i).billingHourEndTime
												- runningInstance.get(i).billingHourStartTime) / 60);
								bw.write(sb.toString());
								bw.newLine();
								bw.flush();

								runningInstance.remove(i);
							}
						}
					}
				}
				if (machineRunning - newMachineReq15 < bill_stop.size()) {
					int server_to_stop = machineRunning - newMachineReq15;
					for (int j = 0; j < server_to_stop; j++) {
						for (int i = 0; i < runningInstance.size(); i++) {
							if (runningInstance.get(i).machineID == bill_stop.get(j).machineID) {
								// System.out.printf("Machine killed:%d \n",
								// runningInstance.get(i).machineID);
								StringBuilder sb = new StringBuilder().append("VM-ID")
										.append(runningInstance.get(i).machineID).append(" ").append("Start Time: ")
										.append(new Date(runningInstance.get(i).billingHourStartTime * 1000))
										.append(" ").append("End Time: ")
										.append(new Date(runningInstance.get(i).billingHourEndTime * 1000)).append(" ")
										.append("Minutes Used: ").append((runningInstance.get(i).billingHourEndTime
												- runningInstance.get(i).billingHourStartTime) / 60);
								bw.write(sb.toString());
								bw.newLine();
								bw.flush();
								runningInstance.remove(i);
							}
						}
					}
				}
			}
		}

		for (int i = 0; i < runningInstance.size(); i++) {
			if (timeStamp == runningInstance.get(i).endOfActivePeriod) {
				// runningInstance.get(i).startOfActivePeriod =
				// runningInstance.get(i).billingHourEndTime
				// + timeTakenToActive;
				runningInstance.get(i).endOfActivePeriod += billingPeriod;
				runningInstance.get(i).billingHourEndTime += billingPeriod;
			}
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
