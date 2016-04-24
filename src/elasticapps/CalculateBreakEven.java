package elasticapps;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import ilog.concert.*;
import ilog.cplex.*;

public class CalculateBreakEven {
	static final String USER_ARRIVAL_LOG = "/Users/subramanya/Documents/workspace/autoscaling/logs/demand_dataset.csv";
	static double costOfRIPerHour = 0.6;
	static double costOfODPerHour = 0.8;
	static double upFronCost = 4607;
	static double discount = 0.37;
	static int numberOfHoursInUse = 480;

	public static void main(String[] args) throws IOException, IloException {
		BufferedReader br;
		br = new BufferedReader(new FileReader(USER_ARRIVAL_LOG));

		// Optimize

		try {
			IloCplex cplex = new IloCplex();
			ArrayList<Integer> reservedInstances = new ArrayList<>();
			ArrayList<Integer> onDemandInstances = new ArrayList<>();

			for (int i = 0; i < numberOfHoursInUse; i++) {
				String line = br.readLine();
				String[] values = line.split(",");

				long timeT = Long.parseLong(values[0]);
				int vmDemand = Integer.parseInt(values[1]);
				
				double[] lb = { 1.0, 1.0};
				double[] ub = { Double.MAX_VALUE, Double.MAX_VALUE };
				
				
			}

			double[] lb = { 0.0, 0.0, 0.0 };
			double[] ub = { 40.0, Double.MAX_VALUE, Double.MAX_VALUE };
			IloNumVar[] x = cplex.numVarArray(3, lb, ub);
			double[] objvals = { 1.0, 2.0, 3.0 };
			cplex.addMaximize(cplex.scalProd(x, objvals));
			cplex.addLe(cplex.sum(cplex.prod(-1.0, x[0]), cplex.prod(1.0, x[1]), cplex.prod(1.0, x[2])), 20.0);
			cplex.addLe(cplex.sum(cplex.prod(1.0, x[0]), cplex.prod(-3.0, x[1]), cplex.prod(1.0, x[2])), 30.0);
			if (cplex.solve()) {
				cplex.output().println("Solution status = " + cplex.getStatus());
				cplex.output().println("Solution value  = " + cplex.getObjValue());
				double[] val = cplex.getValues(x);
				int ncols = cplex.getNcols();
				for (int j = 0; j < ncols; ++j)
					cplex.output().println("Column: " + j + " Value = " + val[j]);
			}
			cplex.end();
		} catch (IloException e) {
			System.err.println("Concert exception ’" + e + "’ caught");
		}

	}
}
