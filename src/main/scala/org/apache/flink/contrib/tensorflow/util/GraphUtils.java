package org.apache.flink.contrib.tensorflow.util;

import org.apache.flink.core.fs.FSDataInputStream;
import org.apache.flink.core.fs.FileStatus;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.core.fs.Path;
import org.apache.flink.util.IOUtils;
import org.tensorflow.Graph;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.List;

/**
 */
public class GraphUtils {

	/**
	 * Import a graph from the given {@link Path}.
	 * @param path the filesystem path to import from.
	 * @return a graph instance.
	 * @throws IOException if the path could not be read.
	 * @throws IllegalArgumentException if the graph could not be imported.
	 */
	public static Graph importFromPath(Path path, String prefix) throws IOException {
		FileSystem fs = path.getFileSystem();
		FileStatus status = fs.getFileStatus(path);
		byte[] graphBytes = new byte[(int) status.getLen()];
		try(FSDataInputStream in = fs.open(path)) {
			IOUtils.readFully(in, graphBytes, 0, (int) status.getLen());
		}
		Graph graph = new Graph();
		graph.importGraphDef(graphBytes, prefix);
		return graph;
	}

	public static List<String> readAllLines(Path path, Charset cs) throws IOException {
		FileSystem fs = path.getFileSystem();
		try(FSDataInputStream in = fs.open(path);
			Reader r = new InputStreamReader(in, cs.newDecoder());
			BufferedReader reader = new BufferedReader(r)) {
			List<String> result = new ArrayList<>();
			for (;;) {
				String line = reader.readLine();
				if (line == null)
					break;
				result.add(line);
			}
			return result;
		}
	}

	private GraphUtils() {}
}
