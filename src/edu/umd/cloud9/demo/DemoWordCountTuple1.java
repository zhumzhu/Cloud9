/*
 * Cloud9: A MapReduce Library for Hadoop
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package edu.umd.cloud9.demo;

import java.io.IOException;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reducer;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;

import edu.umd.cloud9.io.Schema;
import edu.umd.cloud9.io.Tuple;

/**
 * <p>
 * Demo that illustrates use of {@link Tuple} objects as intermediate keys in a
 * MapReduce job. Input comes from the Bible+Shakespeare sample collection,
 * packed into a SequenceFile with {@link DemoPackTuples1}. Output shows the
 * count of words on even- and odd-length lines.
 * </p>
 * 
 * <p>
 * Format of the output SequenceFile: The key is a Tuple. The first field of the
 * tuple contains a word and the second field indicates whether the word was
 * found on a even-length or odd-length line. The value is the count of the word
 * on either even- or odd-length lines.
 * </p>
 * 
 * @see DemoWordCountTuple2
 * @see DemoWordCountJSON
 */
public class DemoWordCountTuple1 {

	// create the schema for the tuple that will serve as the key
	private static final Schema KEY_SCHEMA = new Schema();

	// define the schema statically
	static {
		KEY_SCHEMA.addField("Token", String.class, "");
		KEY_SCHEMA.addField("EvenOrOdd", Integer.class, new Integer(1));
	}

	// mapper that emits tuple as the key, and value '1' for each occurrence
	public static class MyMapper extends MapReduceBase implements
			Mapper<LongWritable, Tuple, Tuple, IntWritable> {

		// define value '1' statically so we can reuse the object, i.e., avoid
		// unnecessary object creation
		private final static IntWritable one = new IntWritable(1);

		// once again, reuse tuples if possible
		private Tuple tupleOut = KEY_SCHEMA.instantiate();

		public void map(LongWritable key, Tuple tupleIn,
				OutputCollector<Tuple, IntWritable> output, Reporter reporter) throws IOException {

			// the input value is a tuple; get field 0
			String line = (String) tupleIn.get(0);
			StringTokenizer itr = new StringTokenizer(line);
			while (itr.hasMoreTokens()) {
				String token = itr.nextToken();

				// put new values into the tuple
				tupleOut.set("Token", token);
				tupleOut.set("EvenOrOdd", line.length() % 2);

				// emit key-value pair
				output.collect(tupleOut, one);
			}
		}
	}

	// reducer counts up tuple occurrences
	public static class MyReducer extends MapReduceBase implements
			Reducer<Tuple, IntWritable, Tuple, IntWritable> {
		private final static IntWritable SumValue = new IntWritable();

		public void reduce(Tuple tupleKey, Iterator<IntWritable> values,
				OutputCollector<Tuple, IntWritable> output, Reporter reporter) throws IOException {
			// sum values
			int sum = 0;
			while (values.hasNext()) {
				sum += values.next().get();
			}

			// keep original tuple key, emit sum of counts as value
			SumValue.set(sum);
			output.collect(tupleKey, SumValue);
		}
	}

	// dummy constructor
	private DemoWordCountTuple1() {
	}

	/**
	 * Runs the demo.
	 */
	public static void main(String[] args) throws IOException {
		String inputPath = "/shared/sample-input/bible+shakes.nopunc.tuple1.packed";
		String outputPath = "DemoWordCountTuple1";
		int numMapTasks = 20;
		int numReduceTasks = 20;

		JobConf conf = new JobConf(DemoWordCountTuple1.class);
		conf.setJobName("DemoWordCountTuple1");

		conf.setNumMapTasks(numMapTasks);
		conf.setNumReduceTasks(numReduceTasks);

		FileInputFormat.setInputPaths(conf, new Path(inputPath));
		FileOutputFormat.setOutputPath(conf, new Path(outputPath));

		conf.setInputFormat(SequenceFileInputFormat.class);
		conf.setOutputKeyClass(Tuple.class);
		conf.setOutputValueClass(IntWritable.class);
		conf.setOutputFormat(SequenceFileOutputFormat.class);

		conf.setMapperClass(MyMapper.class);
		conf.setCombinerClass(MyReducer.class);
		conf.setReducerClass(MyReducer.class);

		// Delete the output directory if it exists already
		Path outputDir = new Path(outputPath);
		FileSystem.get(conf).delete(outputDir, true);

		JobClient.runJob(conf);
	}
}
