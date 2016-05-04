package elasticapps;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

class VmInstance implements Comparable {

    public long billingHourStartTime;
    public long billingHourEndTime;

    public long startOfActivePeriod;
    public long endOfActivePeriod;

    public boolean canExtend = true;

    public int machineID = 0;

    public VmInstance(int machineID, long billingHourStartTime, long billingHourEndTime, long startOfActivePeriod,
                      long endOfActivePeriod) {

        this.machineID = machineID;

        this.billingHourStartTime = billingHourStartTime;
        this.billingHourEndTime = billingHourEndTime;

        this.startOfActivePeriod = startOfActivePeriod;
        this.endOfActivePeriod = endOfActivePeriod;

    }

    @Override
    public int compareTo(Object o) {
        long startOfComp = ((VmInstance) o).billingHourEndTime;
        return (int) (this.billingHourEndTime - startOfComp);
    }
}

public class elasticapp {

    static final String USER_ARRIVAL_LOG = "/Users/subramanya/Documents/workspace/elasticapps/logs/rtc_created_dataset.csv";

    static final String USER_DEMAND_DATA_SET = "/Users/subramanya/Documents/workspace/elasticapps/logs/demand_available_billing_dataset.csv";

    static final String RI_ODI_ACTIVE_BILLING_DATA_SET = "/Users/subramanya/Documents/workspace/elasticapps/logs/RI_ODI_ACTIVE_BILLING_DATA_SET.csv";

    static final String VM_BILLING_DATA_SET = "/Users/subramanya/Documents/workspace/elasticapps/logs/vms_billing_hours.csv";

    static final String VM_BILLING_OF_ODI_DATA_SET = "/Users/subramanya/Documents/workspace/elasticapps/logs/VM_BILLING_OF_ODI_DATA_SET.csv";

    static final String VM_BILLING_OF_RI_DATA_SET = "/Users/subramanya/Documents/workspace/elasticapps/logs/VM_BILLING_OF_RI_DATA_SET.csv";


    static final String CSV_DELIMITOR = ",";
    static long startTimeStamp;
    static ArrayList<VmInstance> runningInstance = new ArrayList<>();
    static int machineID = 0;
    static int machineIDWithRI = 0;
    // Number user can loaded to each VM.
    static int numberOfUserPerInstance = 120;

    static int secondInMin = 60;
    // Time needed for each VM to start.
    static int timeTakenToActive = 5 * secondInMin;
    // Time needed for each VM to shutdown.
    static int timetakenToShutdown = 10 * secondInMin;
    // Look ahead for scale up.
    static int scaleUpLookAhead = 5 * secondInMin;
    // Look ahead for scale down.
    static int scaleDownLookAhead = 15 * secondInMin;
    // hourly billing.
    static int billingPeriod = 60 * secondInMin;

    // Cost of RI
    static double riCost = 0.65;
    // Cost of ODI
    static double odiCost = 0.95;
    // List of RI
    static ArrayList<VmInstance> runningRiInstances = new ArrayList<>();
    // List of ODI
    static ArrayList<VmInstance> runningOdiInstances = new ArrayList<>();
    // Total RI
    static int totalRI = 1;


    public static void main(String[] args) throws IOException, InterruptedException {
        String line = "";
        BufferedReader br;
        BufferedWriter bw,bwRI;
        long timeStampT = 0;
        int totalRequestAtT = 0;
        String[] dataRow;
        try {
            br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));
            bw = new BufferedWriter(new FileWriter(USER_DEMAND_DATA_SET));
            bwRI =new BufferedWriter(new FileWriter(RI_ODI_ACTIVE_BILLING_DATA_SET));
            for (int i = 0; i < 5000; i++) {
                line = br.readLine();
                dataRow = line.split(CSV_DELIMITOR);
                timeStampT = Long.parseLong(dataRow[0]);
                totalRequestAtT = Integer.parseInt(dataRow[1]);
                if (i == 0) {
                    startTimeStamp = timeStampT;
                }
                scalingDecision(timeStampT);
                scalingDecisionWithRI(timeStampT);


                int countOfBilling = 0;
                for (int j = 0; j < runningInstance.size(); j++) {
                    if (timeStampT < runningInstance.get(j).billingHourEndTime)
                        countOfBilling += 1;
                }

                int countOfActive = 0;
                for (int j = 0; j < runningInstance.size(); j++) {
                    if (timeStampT >=  runningInstance.get(j).startOfActivePeriod && timeStampT <= runningInstance.get(j).endOfActivePeriod)
                        countOfActive += 1;
                }

                // Count with RI.

                int countOfRiBilling = 0;
                for (int j = 0; j < runningRiInstances.size(); j++) {
                    if (timeStampT < runningRiInstances.get(j).billingHourEndTime)
                        countOfRiBilling += 1;
                }

                int countOfOdiBilling = 0;
                for (int j = 0; j < runningOdiInstances.size(); j++) {
                    if (timeStampT < runningOdiInstances.get(j).billingHourEndTime)
                        countOfOdiBilling += 1;
                }

                int countOfOdiActive = 0;
                for (int j = 0; j < runningOdiInstances.size(); j++) {
                    if (timeStampT >=  runningOdiInstances.get(j).startOfActivePeriod && timeStampT <= runningOdiInstances.get(j).endOfActivePeriod)
                        countOfOdiActive += 1;
                }


                System.out.println("Stamp: " + timeStampT+ " Time: " + new Date(timeStampT * 1000) + " Users: " + totalRequestAtT + " Demand: "
                        + (int) Math.ceil((double)totalRequestAtT / numberOfUserPerInstance) + " Active: " + countOfActive
                        + " Billing: " + countOfBilling);
                System.out.println("Stamp: " + timeStampT+ " Time: " + new Date(timeStampT * 1000) + " Users: " + totalRequestAtT + " Demand: "
                        + (int) Math.ceil((double)totalRequestAtT / numberOfUserPerInstance) + " RI Count: " + countOfRiBilling
                        + " ODI Active: " + countOfOdiActive + " ODI Billing: " + countOfOdiBilling);
                bw.write(timeStampT + "," + new Date(timeStampT * 1000) + "," + totalRequestAtT + ","
                        + (int) Math.ceil((double)totalRequestAtT / numberOfUserPerInstance) + "," + countOfActive + ","
                        + countOfBilling);
                bwRI.write(timeStampT + "," + new Date(timeStampT * 1000) + "," + totalRequestAtT + ","
                        + (int) Math.ceil((double)totalRequestAtT / numberOfUserPerInstance) + "," + countOfRiBilling + ","
                        + countOfOdiActive + "," + countOfOdiBilling);
                bw.newLine();
                bwRI.newLine();
            }

            bw.close();
            bwRI.close();
            BufferedWriter bw1 = new BufferedWriter(new FileWriter(VM_BILLING_DATA_SET, true));
            for (int i = 0; i < runningInstance.size(); i++) {
                if(runningInstance.get(i).canExtend) {
                    StringBuilder sb = new StringBuilder().append("VM-ID ").append(runningInstance.get(i).machineID)
                            .append(" ").append("Billing Start Time: ")
                            .append(new Date(runningInstance.get(i).billingHourStartTime * 1000)).append(" ")
                            .append("Billing End Time: ").append(new Date(runningInstance.get(i).billingHourEndTime * 1000))
                            .append(" ").append("Active Start Time: ")
                            .append(new Date(runningInstance.get(i).startOfActivePeriod * 1000)).append(" ")
                            .append("Active End Time: ").append(new Date(runningInstance.get(i).endOfActivePeriod * 1000))
                            .append(" ").append("Minutes Used: ").append((runningInstance.get(i).billingHourEndTime
                                    - runningInstance.get(i).billingHourStartTime) / 60);
                    bw1.write(sb.toString());
                    bw1.newLine();
                    bw1.flush();
                }
            }
            bw1.close();

            bw1 = new BufferedWriter(new FileWriter(VM_BILLING_OF_ODI_DATA_SET, true));
            for (int i = 0; i < runningOdiInstances.size(); i++) {
                if(runningOdiInstances.get(i).canExtend) {
                    StringBuilder sb = new StringBuilder().append("VM-ID ").append(runningOdiInstances.get(i).machineID)
                            .append(" ").append("Billing Start Time: ")
                            .append(new Date(runningOdiInstances.get(i).billingHourStartTime * 1000)).append(" ")
                            .append("Billing End Time: ").append(new Date(runningOdiInstances.get(i).billingHourEndTime * 1000))
                            .append(" ").append("Active Start Time: ")
                            .append(new Date(runningOdiInstances.get(i).startOfActivePeriod * 1000)).append(" ")
                            .append("Active End Time: ").append(new Date(runningOdiInstances.get(i).endOfActivePeriod * 1000))
                            .append(" ").append("Minutes Used: ").append((runningOdiInstances.get(i).billingHourEndTime
                                    - runningOdiInstances.get(i).billingHourStartTime) / 60)
                            .append(" ").append("Cost: ").append((((runningOdiInstances.get(i).billingHourEndTime
                                    - runningOdiInstances.get(i).billingHourStartTime) / 60) / 60) * odiCost);
                    bw1.write(sb.toString());
                    bw1.newLine();
                    bw1.flush();
                }
            }
            bw1.close();


            bw1 = new BufferedWriter(new FileWriter(VM_BILLING_OF_RI_DATA_SET, true));
            for (int i = 0; i < runningRiInstances.size(); i++) {
                if(runningRiInstances.get(i).canExtend) {
                    StringBuilder sb = new StringBuilder().append("VM-ID ").append(runningRiInstances.get(i).machineID)
                            .append(" ").append("Billing Start Time: ")
                            .append(new Date(runningRiInstances.get(i).billingHourStartTime * 1000)).append(" ")
                            .append("Billing End Time: ").append(new Date(runningRiInstances.get(i).billingHourEndTime * 1000))
                            .append(" ").append("Active Start Time: ")
                            .append(new Date(runningRiInstances.get(i).startOfActivePeriod * 1000)).append(" ")
                            .append("Active End Time: ").append(new Date(runningRiInstances.get(i).endOfActivePeriod * 1000))
                            .append(" ").append("Minutes Used: ").append((runningRiInstances.get(i).billingHourEndTime
                                    - runningRiInstances.get(i).billingHourStartTime) / 60)
                            .append(" ").append("Cost: ").append((((runningRiInstances.get(i).billingHourEndTime
                                    - runningRiInstances.get(i).billingHourStartTime) / 60)/60) * riCost);
                    bw1.write(sb.toString());
                    bw1.newLine();
                    bw1.flush();
                }
            }
            bw1.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void scalingDecision(long timeStamp) throws IOException {


        //if(timeStamp == 1432821000) {
        //    System.out.println();
        //}

        // Get predicted load.
        // Check for scale up or down.
        ArrayList<Integer> userInInterval5 = getRequestCountsInTimeRange(timeStamp, timeStamp + timeTakenToActive);
        Collections.sort(userInInterval5);
        int maxUserInterval5 = userInInterval5.get(userInInterval5.size() - 1);
        int machineReq = (int) Math.ceil((double)maxUserInterval5 / numberOfUserPerInstance);

        if (timeStamp == startTimeStamp) {
            for (int i = 0; i < machineReq; i++) {
                // machine id
                // billingHourStartTime,
                // billingHourEndTime,
                // startOfActivePeriod,
                // endOfActivePeriod
                machineID += 1;
                runningInstance.add(new VmInstance(machineID, timeStamp, timeStamp + billingPeriod,
                        timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));
            }
            return;
        }

        // Machines currently being billed
        int machineRunning = 0;

        for (int i = 0; i < runningInstance.size(); i++) {
            if(timeStamp <= runningInstance.get(i).endOfActivePeriod)
                machineRunning+=1;
        }

        if (machineReq > machineRunning) {
            int machinesToAdd = machineReq - machineRunning;
            for (int i = 0; i < machinesToAdd; i++) {
                // machine id
                // billingHourStartTime,
                // billingHourEndTime,
                // startOfActivePeriod,
                // endOfActivePeriod
                machineID += 1;
                runningInstance.add(new VmInstance(machineID, timeStamp, timeStamp + billingPeriod,
                        timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));
            }
        }

        ArrayList<Integer> usersInInterval15 = getRequestCountsInTimeRange(timeStamp, timeStamp + scaleDownLookAhead);
        Collections.sort(usersInInterval15);
        int maxUserInterval15 = usersInInterval15.get(usersInInterval15.size() - 1);
        int newMachineReq15 = (int) Math.ceil((double)maxUserInterval15 / numberOfUserPerInstance);

        BufferedWriter bw = new BufferedWriter(new FileWriter(VM_BILLING_DATA_SET, true));



        if (machineRunning > newMachineReq15) {
            ArrayList<VmInstance> vmsEndingActivePeriod = new ArrayList<>();
            for (int i = 0; i < runningInstance.size(); i++) {
                if (runningInstance.get(i).endOfActivePeriod == timeStamp) {
                    vmsEndingActivePeriod.add(runningInstance.get(i));
                }
            }

            int totalVmToKill = machineRunning - newMachineReq15;

            if (vmsEndingActivePeriod.size() > 0) {
                // kill all vm's which are ending active period.
                if ( totalVmToKill >= vmsEndingActivePeriod.size() ) {
                    for (int j = 0; j < vmsEndingActivePeriod.size(); j++) {
                        for (int i = 0; i < runningInstance.size(); i++) {
                            if (runningInstance.get(i).machineID == vmsEndingActivePeriod.get(j).machineID) {
                                runningInstance.get(i).canExtend = false;
                                StringBuilder sb = new StringBuilder().append("VM-ID ")
                                        .append(runningInstance.get(i).machineID).append(" ")
                                        .append("Billing Start Time: ")
                                        .append(new Date(runningInstance.get(i).billingHourStartTime * 1000))
                                        .append(" ").append("Billing End Time: ")
                                        .append(new Date(runningInstance.get(i).billingHourEndTime * 1000)).append(" ")
                                        .append("Active Start Time: ")
                                        .append(new Date(runningInstance.get(i).startOfActivePeriod * 1000)).append(" ")
                                        .append("Active End Time: ")
                                        .append(new Date(runningInstance.get(i).endOfActivePeriod * 1000)).append(" ")
                                        .append("Minutes Used: ").append((runningInstance.get(i).billingHourEndTime
                                                - runningInstance.get(i).billingHourStartTime) / 60).append(" ")
                                        .append("Cost: ").append(odiCost * ((runningInstance.get(i).billingHourEndTime
                                                - runningInstance.get(i).billingHourStartTime) / 60));
                                bw.write(sb.toString());
                                bw.newLine();
                                bw.flush();
                            }
                        }
                    }
                }
                // kill only subset of vm's ending active period.
                if (totalVmToKill < vmsEndingActivePeriod.size()) {
                    for (int j = 0; j < totalVmToKill; j++) {
                        for (int i = 0; i < runningInstance.size(); i++) {
                            if (runningInstance.get(i).machineID == vmsEndingActivePeriod.get(j).machineID) {
                                runningInstance.get(i).canExtend = false;
                                StringBuilder sb = new StringBuilder().append("VM-ID ")
                                        .append(runningInstance.get(i).machineID).append(" ")
                                        .append("Billing Start Time: ")
                                        .append(new Date(runningInstance.get(i).billingHourStartTime * 1000))
                                        .append(" ").append("Billing End Time: ")
                                        .append(new Date(runningInstance.get(i).billingHourEndTime * 1000)).append(" ")
                                        .append("Active Start Time: ")
                                        .append(new Date(runningInstance.get(i).startOfActivePeriod * 1000)).append(" ")
                                        .append("Active End Time: ")
                                        .append(new Date(runningInstance.get(i).endOfActivePeriod * 1000)).append(" ")
                                        .append("Minutes Used: ").append((runningInstance.get(i).billingHourEndTime
                                                - runningInstance.get(i).billingHourStartTime) / 60).append(" ")
                                        .append("Cost: ").append(odiCost * ((runningInstance.get(i).billingHourEndTime
                                                - runningInstance.get(i).billingHourStartTime) / 60));
                                bw.write(sb.toString());
                                bw.newLine();
                                bw.flush();
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < runningInstance.size(); i++) {
            if (timeStamp == runningInstance.get(i).endOfActivePeriod && runningInstance.get(i).canExtend) {
                runningInstance.get(i).endOfActivePeriod += billingPeriod;
                runningInstance.get(i).billingHourEndTime += billingPeriod;
            }
        }
    }



    private static void scalingDecisionWithRI(long timeStamp) throws IOException {


        //if(timeStamp == 1432821000) {
        //    System.out.println();
        //}

        // Get predicted load.
        // Check for scale up or down.
        ArrayList<Integer> userInInterval5 = getRequestCountsInTimeRange(timeStamp, timeStamp + timeTakenToActive);
        Collections.sort(userInInterval5);
        int maxUserInterval5 = userInInterval5.get(userInInterval5.size() - 1);
        int machineReq = (int) Math.ceil((double)maxUserInterval5 / numberOfUserPerInstance);

        if (timeStamp == startTimeStamp) {
            for (int i = 0; i < totalRI; i++) {
                machineIDWithRI += 1;
                runningRiInstances.add(new VmInstance(machineIDWithRI, timeStamp, timeStamp + billingPeriod,
                        timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));
            }
            machineReq = machineReq - totalRI;
            for (int i = 0; i < machineReq; i++) {
                // machine id
                // billingHourStartTime,
                // billingHourEndTime,
                // startOfActivePeriod,
                // endOfActivePeriod
                machineIDWithRI += 1;
                    runningOdiInstances.add(new VmInstance(machineIDWithRI, timeStamp, timeStamp + billingPeriod,
                            timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));
            }
            return;
        }

        // Machines currently being reserved.
        int machineRunning = runningRiInstances.size();

        // Machines running in ODI
        for (int i = 0; i < runningOdiInstances.size(); i++) {
            if(timeStamp <= runningOdiInstances.get(i).endOfActivePeriod)
                machineRunning+=1;
        }

        if (machineReq > machineRunning) {
            int machinesToAdd = machineReq - machineRunning;
            for (int i = 0; i < machinesToAdd; i++) {
                // machine id
                // billingHourStartTime,
                // billingHourEndTime,
                // startOfActivePeriod,
                // endOfActivePeriod
                machineIDWithRI += 1;
                    runningOdiInstances.add(new VmInstance(machineIDWithRI, timeStamp, timeStamp + billingPeriod,
                            timeStamp + timeTakenToActive, (timeStamp + billingPeriod) - timetakenToShutdown));

            }
        }

        ArrayList<Integer> usersInInterval15 = getRequestCountsInTimeRange(timeStamp, timeStamp + scaleDownLookAhead);
        Collections.sort(usersInInterval15);
        int maxUserInterval15 = usersInInterval15.get(usersInInterval15.size() - 1);
        int newMachineReq15 = (int) Math.ceil((double)maxUserInterval15 / numberOfUserPerInstance);

        BufferedWriter bw = new BufferedWriter(new FileWriter(VM_BILLING_OF_ODI_DATA_SET, true));


        if (machineRunning > newMachineReq15) {
            ArrayList<VmInstance> vmsEndingActivePeriod = new ArrayList<>();
            for (int i = 0; i < runningOdiInstances.size(); i++) {
                if (runningOdiInstances.get(i).endOfActivePeriod == timeStamp) {
                    vmsEndingActivePeriod.add(runningOdiInstances.get(i));
                }
            }

            int totalVmToKill = machineRunning - newMachineReq15;

            if (vmsEndingActivePeriod.size() > 0) {
                // kill all vm's which are ending active period.
                if ( totalVmToKill >= vmsEndingActivePeriod.size() ) {
                    for (int j = 0; j < vmsEndingActivePeriod.size(); j++) {
                        for (int i = 0; i < runningOdiInstances.size(); i++) {
                            if (runningOdiInstances.get(i).machineID == vmsEndingActivePeriod.get(j).machineID) {
                                runningOdiInstances.get(i).canExtend = false;
                                StringBuilder sb = new StringBuilder().append("VM-ID ")
                                        .append(runningOdiInstances.get(i).machineID).append(" ")
                                        .append("Billing Start Time: ")
                                        .append(new Date(runningOdiInstances.get(i).billingHourStartTime * 1000))
                                        .append(" ").append("Billing End Time: ")
                                        .append(new Date(runningOdiInstances.get(i).billingHourEndTime * 1000)).append(" ")
                                        .append("Active Start Time: ")
                                        .append(new Date(runningOdiInstances.get(i).startOfActivePeriod * 1000)).append(" ")
                                        .append("Active End Time: ")
                                        .append(new Date(runningOdiInstances.get(i).endOfActivePeriod * 1000)).append(" ")
                                        .append("Minutes Used: ").append((runningOdiInstances.get(i).billingHourEndTime
                                                - runningOdiInstances.get(i).billingHourStartTime) / 60).append(" ")
                                        .append("Cost: ").append(odiCost * (((runningOdiInstances.get(i).billingHourEndTime
                                                - runningOdiInstances.get(i).billingHourStartTime) / 60)/60));
                                bw.write(sb.toString());
                                bw.newLine();
                                bw.flush();
                            }
                        }
                    }
                }
                // kill only subset of vm's ending active period.
                if (totalVmToKill < vmsEndingActivePeriod.size()) {
                    for (int j = 0; j < totalVmToKill; j++) {
                        for (int i = 0; i < runningOdiInstances.size(); i++) {
                            if (runningOdiInstances.get(i).machineID == vmsEndingActivePeriod.get(j).machineID) {
                                runningOdiInstances.get(i).canExtend = false;
                                StringBuilder sb = new StringBuilder().append("VM-ID ")
                                        .append(runningOdiInstances.get(i).machineID).append(" ")
                                        .append("Billing Start Time: ")
                                        .append(new Date(runningOdiInstances.get(i).billingHourStartTime * 1000))
                                        .append(" ").append("Billing End Time: ")
                                        .append(new Date(runningOdiInstances.get(i).billingHourEndTime * 1000)).append(" ")
                                        .append("Active Start Time: ")
                                        .append(new Date(runningOdiInstances.get(i).startOfActivePeriod * 1000)).append(" ")
                                        .append("Active End Time: ")
                                        .append(new Date(runningOdiInstances.get(i).endOfActivePeriod * 1000)).append(" ")
                                        .append("Minutes Used: ").append((runningOdiInstances.get(i).billingHourEndTime
                                                - runningOdiInstances.get(i).billingHourStartTime) / 60).append(" ")
                                        .append("Cost: ").append(odiCost * (((runningOdiInstances.get(i).billingHourEndTime
                                                - runningOdiInstances.get(i).billingHourStartTime) / 60)/60));
                                bw.write(sb.toString());
                                bw.newLine();
                                bw.flush();
                            }
                        }
                    }
                }
            }
        }

        for (int i = 0; i < runningOdiInstances.size(); i++) {
            if (timeStamp == runningOdiInstances.get(i).endOfActivePeriod && runningOdiInstances.get(i).canExtend) {
                runningOdiInstances.get(i).endOfActivePeriod += billingPeriod;
                runningOdiInstances.get(i).billingHourEndTime += billingPeriod;
            }
        }

        for (int i = 0; i < runningRiInstances.size(); i++) {
            if (timeStamp == runningRiInstances.get(i).endOfActivePeriod) {
                runningRiInstances.get(i).endOfActivePeriod += billingPeriod;
                runningRiInstances.get(i).billingHourEndTime += billingPeriod;
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
