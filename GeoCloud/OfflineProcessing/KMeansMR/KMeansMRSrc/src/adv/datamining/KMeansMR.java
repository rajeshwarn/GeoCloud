package adv.datamining;

import java.io.File;

import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;

public class KMeansMR {
	
	/*
	 *	KMeans for hadoop map-reduce
	 *
	 *	@inputDirectory: folder that contains input data, should be comma delimited like: [0,6.915074975090942,1.987097505079365]
	 *					 the first part should be integer ID, the coordinates can be any dimension, by distance will be calculated
	 *					 in Euclidean distance 
	 *
	 *  @outputDirectory: folder for output, will be added a trailing number for each iteration
	 *  
	 *  @numIters: number of iterations assigned by user
	 *  
	 *  @currentCenterDirectory: mapper will write in its clean up function
	 *  @nextCenterDirectory: for mapper to read in its setup function and for reducer to write for future iterations
	 *  					  
	 *  The relationship between currentCenterDirectory and nextCenterDirectory is shown below 
	 *  
	 *  |-----------------|
	 *  |  currentCenter  |<------cleanup------|
	 *  |-----------------|                    | 
	 *                                         |
	 *  |-----------------|                    |
	 *  |                 |------setup-----> Mapper
	 *  |    nextCenter   |
	 *  |                 |<----cleanup---- Reducer
	 *  |-----------------|
	 */
	
	static void printUsage() {
		System.out
				.println("KMeansMR <inputDirectory> <outputDirectory> <numIters> <currentCenterDirectory> <nextCenterDirectory>");
		System.exit(-1);
	}

	public static int main(String[] args) throws Exception {

		// check arguments
		if (args.length != 5) {
			printUsage();
			return 1;
		}

		// repeat the whole thing until reach assigned times
		for (int i = 0; i < Integer.parseInt(args[2]); i++) {

			// Get the default configuration object
			Configuration conf = new Configuration();

			/* 
			 * List the center files in `nextCenter` folder.
			 * Filenames should start with `centers`.
			 * This convention also apply to reducers because 
			 * it will write new centers for future iterations.
			 */
			FileSystem fs = FileSystem.get(conf);
			Path path = new Path(args[4] + File.separator + "iteration_" + i);
			FileStatus fstatus[] = fs.listStatus(path);

			StringBuilder nextCluster = new StringBuilder();

			for (FileStatus f : fstatus) {
				if (f.getPath().toUri().getPath().contains("centers")) {
					nextCluster.append(f.getPath().toUri().getPath());
					nextCluster.append(":");
				}
			}
			nextCluster.deleteCharAt(nextCluster.length() - 1);

			/* 
			 * (1) `mapperReadCenter` contains the filenames for 
			 * center files generated by previous reducer, while
			 * 
			 * (2) `nextCenter` is the folder for reducer to write 
			 * to and mapper to read from
			 * 
			 * (3) `currentCenter` is written by mapper to indicate
			 * the centers used for current iteration
			 */
			conf.set("mapperReadCenter", nextCluster.toString());
			conf.set("nextCenter", args[4]);
			conf.set("currentCenter", args[3]);
			conf.set("iteration", Integer.toString(i));

			// get the new job
			Job job = new Job(conf);
			job.setJobName("KMeans clustering");

			job.setMapOutputKeyClass(Text.class);
			job.setMapOutputValueClass(Text.class);
			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(Text.class);

			job.setMapperClass(KMeansMRMapper.class);
			job.setReducerClass(KMeansMRReducer.class);

			job.setInputFormatClass(TextInputFormat.class);
			job.setOutputFormatClass(TextOutputFormat.class);

			// set the input and output files
			TextInputFormat.setInputPaths(job, args[0]);
			TextOutputFormat.setOutputPath(job, new Path(args[1] + (i + 1)));

			job.setJarByClass(KMeansMR.class);

			job.setNumReduceTasks(2);

			// start the job
			System.out.println("Starting iteration " + (i+1));
			int exitCode = job.waitForCompletion(true) ? 0 : 1;

			if (exitCode != 0) {
				System.out.println("Job Failed!!!");
				return exitCode;
			}
		}

		return 0;
	}
}
