package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.CUtils;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.milaboratory.mitools.cli.Action;
import com.milaboratory.mitools.cli.ActionHelper;
import com.milaboratory.mitools.cli.ActionParameters;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriterI;
import com.milaboratory.mixcr.reference.GeneFeature;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.util.VDJCAlignmentsDifferenceReader;
import com.milaboratory.util.SmartProgressReporter;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Bolotin
 * @author Stanislav Poslavsky
 */
public final class ActionAlignmentsDiff implements Action {
    final DiffParameters parameters = new DiffParameters();

    @Override
    public void go(ActionHelper actionHelper) throws Exception {
        try (VDJCAlignmentsReader reader1 = new VDJCAlignmentsReader(parameters.get1(), LociLibraryManager.getDefault());
             VDJCAlignmentsReader reader2 = new VDJCAlignmentsReader(parameters.get2(), LociLibraryManager.getDefault());
             VDJCAlignmentsWriterI only1 = parameters.onlyFirst == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(parameters.onlyFirst);
             VDJCAlignmentsWriterI only2 = parameters.onlySecond == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(parameters.onlySecond);
             VDJCAlignmentsWriterI diff1 = parameters.diff1 == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(parameters.diff1);
             VDJCAlignmentsWriterI diff2 = parameters.diff1 == null ?
                     VDJCAlignmentsWriterI.DummyWriter.INSTANCE : new VDJCAlignmentsWriter(parameters.diff2);
             PrintStream report = parameters.report().equals(".") ? System.out : new PrintStream(new FileOutputStream(parameters.report()))
        ) {
            if (reader1.getNumberOfReads() > reader2.getNumberOfReads())
                SmartProgressReporter.startProgressReport("Analyzing diff", reader1);
            else
                SmartProgressReporter.startProgressReport("Analyzing diff", reader2);

            long same = 0, onlyIn1 = 0, onlyIn2 = 0, diffFeature = 0, justDiff = 0;
            long[] diffHits = new long[GeneType.NUMBER_OF_TYPES];

            only1.header(reader1.getParameters(), reader1.getUsedAlleles());
            diff1.header(reader1.getParameters(), reader1.getUsedAlleles());
            only2.header(reader2.getParameters(), reader2.getUsedAlleles());
            diff2.header(reader2.getParameters(), reader2.getUsedAlleles());

            VDJCAlignmentsDifferenceReader diffReader = new VDJCAlignmentsDifferenceReader(reader1, reader2,
                    parameters.getFeature(), parameters.hitsCompareLevel);
            for (VDJCAlignmentsDifferenceReader.Diff diff : CUtils.it(diffReader)) {
                switch (diff.status) {
                    case AlignmentsAreSame: ++same; break;
                    case AlignmentPresentOnlyInFirst: ++onlyIn1; only1.write(diff.first); break;
                    case AlignmentPresentOnlyInSecond: ++onlyIn2; only2.write(diff.second); break;
                    case AlignmentsAreDifferent:
                        ++justDiff;

                        diff1.write(diff.first);
                        diff2.write(diff.second);

                        if (diff.reason.diffGeneFeature)
                            ++diffFeature;
                        for (Map.Entry<GeneType, Boolean> e : diff.reason.diffHits.entrySet())
                            if (e.getValue())
                                ++diffHits[e.getKey().ordinal()];
                }
            }

            only1.setNumberOfProcessedReads(onlyIn1);
            only2.setNumberOfProcessedReads(onlyIn2);
            diff1.setNumberOfProcessedReads(justDiff);
            diff2.setNumberOfProcessedReads(justDiff);

            report.println("Completely same reads: " + same);
            report.println("Aligned reads present only in the FIRST  file: " + onlyIn1);
            report.println("Aligned reads present only in the SECOND file: " + onlyIn2);
            report.println("Total number of different reads: " + justDiff);
            report.println("Reads with not same CDR3: " + diffFeature);
            for (GeneType geneType : GeneType.VDJC_REFERENCE)
                report.println("Reads with not same " + geneType.name() + " hits: " + diffHits[geneType.ordinal()]);
        }
    }

    @Override
    public String command() {
        return "alignmentsDiff";
    }

    @Override
    public ActionParameters params() {
        return parameters;
    }
    
    @Parameters(commandDescription = "Calculates the difference between two .vdjca files",
            optionPrefixes = "-")
    public static class DiffParameters extends ActionParametersWithOutput {
        @Parameter(description = "input_file1 input_file2 report", variableArity = true)
        public List<String> parameters = new ArrayList<>();
        @Parameter(names = {"-o1", "--only-in-first"}, description = "output for alignments contained only in the first .vdjca file", variableArity = false)
        public String onlyFirst;
        @Parameter(names = {"-o2", "--only-in-second"}, description = "output for alignments contained only in the second .vdjca file", variableArity = false)
        public String onlySecond;
        @Parameter(names = {"-d1", "--diff-from-first"}, description = "output for alignments from the first file that are different from those alignments in the second file", variableArity = false)
        public String diff1;
        @Parameter(names = {"-d2", "--diff-from-second"}, description = "output for alignments from the second file that are different from those alignments in the first file", variableArity = false)
        public String diff2;
        @Parameter(names = {"-g", "--gene-feature"}, description = "Gene feature to compare", variableArity = false)
        public String geneFeatureToMatch = "CDR3";
        @Parameter(names = {"-l", "--top-hits-level"}, description = "Number of top hits to search for match", variableArity = false)
        public int hitsCompareLevel = 1;

        GeneFeature getFeature() {
            return GeneFeature.parse(geneFeatureToMatch);
        }

        String get1() {
            return parameters.get(parameters.size() - 3);
        }

        String get2() {
            return parameters.get(parameters.size() - 2);
        }

        String report() {
            return parameters.get(parameters.size() - 1);
        }

        @Override
        protected List<String> getOutputFiles() {
            return parameters.subList(parameters.size() - 1, parameters.size());
        }
    }
}
