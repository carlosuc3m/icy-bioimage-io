/*-
 * #%L
 * This project performs Continuous Integration tasks on the JDLL library
 * %%
 * Copyright (C) 2023 Institut Pasteur.
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package io.bioimage.modelrunner.ci;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 
 */
public class ContinuousIntegration {
	
	public static void main(String[] args) {
		String resource_id = "your_resource_id";
        String version_id = "your_version_id";
        
        // Equivalent to Python's rdf_dir / resource_id / version_id
        Path rdfDir = Paths.get("target");

        // Create a matcher for the pattern 'rdf.yaml'
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**.class");

        // Stream and filter the directory contents based on the pattern
        try (Stream<Path> stream = Files.walk(rdfDir)) {
            stream.filter(matcher::matches)
                  .forEach(System.out::println); // Print the matched paths
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	
	public static void main2(String[] args) {
		if (args.length == 0) {
			args = new String[] {};
		}
		
		
		
	}
}
