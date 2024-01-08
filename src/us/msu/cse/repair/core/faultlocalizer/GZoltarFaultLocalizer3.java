package us.msu.cse.repair.core.faultlocalizer;

import java.nio.file.Paths;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;

import us.msu.cse.repair.core.parser.LCNode;

public class GZoltarFaultLocalizer3 implements IFaultLocalizer {
    Set<String> positiveTestMethods;
    Set<String> negativeTestMethods;

    Map<LCNode, Double> faultyLines;

    public GZoltarFaultLocalizer3(String binJavaDir, String binTestDir, Set<String> dependencies,
            String externalProjRoot, String gzoltarDataDir) throws IOException {

        ProcessBuilder pb = new ProcessBuilder();

        Set<String> absoluteDeps = new TreeSet<>();
        for (String dep : dependencies) {
            absoluteDeps.add(Paths.get(dep).toAbsolutePath().toString());
        }

        pb.command("bash", Paths.get(externalProjRoot, "localization", "localize.sh").toAbsolutePath().toString(),
                Paths.get(binJavaDir).toAbsolutePath().toString(), Paths.get(binTestDir).toAbsolutePath().toString(),
                String.join(File.pathSeparator, absoluteDeps), Paths.get(gzoltarDataDir).toAbsolutePath().toString());

        pb.directory(Paths.get(externalProjRoot, "lib").toFile());

        Process p = pb.start();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (p.exitValue() != 0) {
            String error = new String(p.getErrorStream().readAllBytes());
            throw new RuntimeException(
                    String.format("Failed to do fault localization using GZoltar:\n    Command: %s,\n    Error: %s",
                            String.join(" ", pb.command()), error));
        }

        positiveTestMethods = new HashSet<String>();
        negativeTestMethods = new HashSet<String>();
        File testFile = new File(gzoltarDataDir, "tests");
        List<String> allTestMethods = FileUtils.readLines(testFile, "UTF-8");
        for (int i = 1; i < allTestMethods.size(); i++) {
            String info[] = allTestMethods.get(i).trim().split(",");
            if (info[1].trim().equals("PASS"))
                positiveTestMethods.add(info[0].trim());
            else
                negativeTestMethods.add(info[0].trim());
        }

        faultyLines = new HashMap<LCNode, Double>();
        File spectraFile = new File(gzoltarDataDir, "spectra");
        List<String> fLines = FileUtils.readLines(spectraFile, "UTF-8");
        for (int i = 1; i < fLines.size(); i++) {
            String line = fLines.get(i).trim();

            int startIndex = line.indexOf('<');
            int endIndex = line.indexOf('{');
            String className = line.substring(startIndex + 1, endIndex);

            String[] info = line.split("#")[1].split(",");
            int lineNumber = Integer.parseInt(info[0].trim());
            double suspValue = Double.parseDouble(info[1].trim());

            LCNode lcNode = new LCNode(className, lineNumber);
            faultyLines.put(lcNode, suspValue);
        }
    }

    @Override
    public Map<LCNode, Double> searchSuspicious(double thr) {
        Map<LCNode, Double> partFaultyLines = new HashMap<LCNode, Double>();
        for (Map.Entry<LCNode, Double> entry : faultyLines.entrySet()) {
            if (entry.getValue() >= thr)
                partFaultyLines.put(entry.getKey(), entry.getValue());
        }
        return partFaultyLines;
    }

    @Override
    public Set<String> getPositiveTests() {
        return this.positiveTestMethods;
    }

    @Override
    public Set<String> getNegativeTests() {
        return this.negativeTestMethods;
    }
}
