/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.clustering.dirichlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.mahout.clustering.WeightedVectorWritable;
import org.apache.mahout.clustering.kmeans.OutputLogFilter;
import org.apache.mahout.math.VectorWritable;

public class DirichletClusterMapper extends Mapper<WritableComparable<?>, VectorWritable, IntWritable, WeightedVectorWritable> {

  private List<DirichletCluster<VectorWritable>> clusters;
  private DirichletClusterer<VectorWritable> clusterer;

  /* (non-Javadoc)
   * @see org.apache.hadoop.mapreduce.Mapper#map(java.lang.Object, java.lang.Object, org.apache.hadoop.mapreduce.Mapper.Context)
   */
  @Override
  protected void map(WritableComparable<?> key, VectorWritable vector, Context context) throws IOException, InterruptedException {
    clusterer.emitPointToClusters(vector, clusters, context);
  }

  /* (non-Javadoc)
   * @see org.apache.hadoop.mapreduce.Mapper#setup(org.apache.hadoop.mapreduce.Mapper.Context)
   */
  @Override
  protected void setup(Context context) throws IOException, InterruptedException {
    super.setup(context);
    Configuration conf = context.getConfiguration();
    try {
      clusters = getClusters(conf);
      String emitMostLikely = conf.get(DirichletDriver.EMIT_MOST_LIKELY_KEY);
      String threshold = conf.get(DirichletDriver.THRESHOLD_KEY);
      clusterer = new DirichletClusterer<VectorWritable>(Boolean.parseBoolean(emitMostLikely),
                                                         Double.parseDouble(threshold));
    } catch (SecurityException e) {
      throw new IllegalStateException(e);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException(e);
    }
  }

  public static List<DirichletCluster<VectorWritable>> getClusters(Configuration job) {
    String statePath = job.get(DirichletDriver.STATE_IN_KEY);
    List<DirichletCluster<VectorWritable>> clusters = new ArrayList<DirichletCluster<VectorWritable>>();
    try {
      Path path = new Path(statePath);
      FileSystem fs = FileSystem.get(path.toUri(), job);
      FileStatus[] status = fs.listStatus(path, new OutputLogFilter());
      for (FileStatus s : status) {
        SequenceFile.Reader reader = new SequenceFile.Reader(fs, s.getPath(), job);
        try {
          Text key = new Text();
          DirichletCluster<VectorWritable> cluster = new DirichletCluster<VectorWritable>();
          while (reader.next(key, cluster)) {
            clusters.add(cluster);
            cluster = new DirichletCluster<VectorWritable>();
          }
        } finally {
          reader.close();
        }
      }
      return clusters;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

}
