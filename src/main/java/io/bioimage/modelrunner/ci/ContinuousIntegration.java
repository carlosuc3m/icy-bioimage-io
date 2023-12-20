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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.bioimage.modelrunner.bioimageio.description.weights.ModelWeight;
import io.bioimage.modelrunner.bioimageio.description.weights.WeightFormat;
import io.bioimage.modelrunner.tensor.Tensor;
import io.bioimage.modelrunner.utils.Constants;
import io.bioimage.modelrunner.utils.YAMLUtils;

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

	
	public static void runTests(Path rdfDir, String resourceID, String versionID, Path summariesDir, String postfix) throws IOException {
		LinkedHashMap<String, String> summaryDefaults = new LinkedHashMap<String, String>();
		postfix = getJDLLVersion();
		summaryDefaults.put("JDLL_VERSION", postfix);
		
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + resourceID + File.separator + versionID + File.separator + Constants.RDF_FNAME);

        List<Path> rdfFiles = Files.walk(rdfDir).filter(matcher::matches).collect(Collectors.toList());
		
		for (Path rdfPath : rdfFiles) {
			String testName = "Reproduce ouptuts with JDLL " + postfix;
			String error = null;
			String status = null;
			
			Map<String, Object> rdf = new LinkedHashMap<String, Object>();
			try {
				rdf = YAMLUtils.load(rdfPath.toAbsolutePath().toString());
			} catch (Exception ex) {
				error = "Unable to load " + Constants.RDF_FNAME + ": " + ex.toString();
				status = "failed";
			}

			Object rdID = rdf.get("id");
			Object type = rdf.get("type");
			Object weightFormats = rdf.get("weights");
			if (rdID == null || !(rdID instanceof String)) {
				System.out.println("Invalid RDF. Missing/Invalid 'id' in rdf: " + rdfPath.toString());
			} else if (type == null || !(type instanceof String) || !((String) type).equals("model")) {
				status = "skipped";
				error = "not a model RDF";
			} else if (weightFormats == null || !(weightFormats instanceof List)) {
				status = "failed";
				error = "Missing/Invalid weight formats for " + rdID;
			}
			
			if (status != null) {
				List<Object> summary = new ArrayList<Object>();
				Map<String, String> summaryMap = new LinkedHashMap<String, String>();
				summaryMap.put("name", testName);
				summaryMap.put("status", status);
				summaryMap.put("error", error);
				summaryMap.put("source_name", rdfPath.toAbsolutePath().toString());
				summaryMap.putAll(summaryDefaults);
				
				writeSummaries(summariesDir.toAbsolutePath() + File.separator + rdID + File.separator + "test_summary_" + postfix + ".yaml", summary);
				continue;
			}
			
			Map<String, Object> summariesPerWeightFormat = new LinkedHashMap<String, Object>();
			
			
			ModelWeight weights = ModelWeight.build((Map<String, Object>) weightFormats);
			
			for (WeightFormat ww : weights.gettAllSupportedWeightObjects()) {
				Map<String, String> summaryWeightFormat = new LinkedHashMap<String, String>();
				try {
					
				} catch (Exception ex) {
					summaryWeightFormat.put("name", testName);
					summaryWeightFormat.put("status", "failed");
					summaryWeightFormat.put("error", ex.toString());
					summaryWeightFormat.put("traceback", ex.toString());
					summaryWeightFormat.put("source_name", rdfPath.toAbsolutePath().toString());
					summaryWeightFormat.putAll(summaryDefaults);
				}
				summariesPerWeightFormat.put(ww.getFramework(), summaryWeightFormat);
			}

			List<Object> passedReproducedSummaries = new ArrayList<Object>();
			List<Object> failedReproducedSummaries = new ArrayList<Object>();
			List<Object> otherSummaries = new ArrayList<Object>();
			List<String> seenTests = new ArrayList<String>();
			
			for (Entry<String, Object> entry : summariesPerWeightFormat.entrySet()) {
				String wf = entry.getKey();
				List<Map<String, String>> s = (List<Map<String, String>>) entry.getValue();
				for (Map<String, String> ss : s) {
					boolean isOther = !ss.get("name").equals("reproduce test outputs from test inputs");
					if (isOther && seenTests.contains(ss.toString())) {
						continue;
					}
					ss.put("name", ss.get("name") + " (" + wf + ")");
					if (isOther) {
						seenTests.add(ss.toString());
	                    otherSummaries.add(ss);
	                    continue;
					}
					if (status.equals("passed")) passedReproducedSummaries.add(ss);
					else failedReproducedSummaries.add(ss);
				}
			}
			
			List<Object> chosenSummaries = new ArrayList<Object>();
			chosenSummaries.addAll(passedReproducedSummaries);
			chosenSummaries.addAll(failedReproducedSummaries);
			chosenSummaries.addAll(otherSummaries);
			
			writeSummaries(summariesDir.toAbsolutePath() + File.separator + rdID + File.separator + "test_summary_" + postfix + ".yaml", chosenSummaries);
		}
	}
	
	private static void writeSummaries(String summariesPath, List<Object> summaries) throws IOException {
		Path path = Paths.get(summariesPath).getParent();
		if (path != null && !Files.exists(path))
            Files.createDirectories(path);
		YAMLUtils.writeYamlFile(summariesPath, summaries);
	}
	
	/**
	 * Code to automatically get the version of JDLL being used
	 * @return the JDLL version being used
	 */
	public static String getJDLLVersion() {
		String version = "UNKNOWN";
        try (InputStream manifestStream = Tensor.class.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF")) {
            if (manifestStream != null) {
                Manifest manifest = new Manifest(manifestStream);
                java.util.jar.Attributes attrs = manifest.getMainAttributes();
                version = attrs.getValue("Implementation-Version");
            }
        } catch (Exception e) {
        }
        return version;
    }
}
