/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.vdjaligners;

import cc.redberry.pipe.Processor;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.util.HashFunctions;
import com.milaboratory.util.RandomUtil;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.*;

public abstract class VDJCAligner<R extends SequenceRead> implements Processor<R, VDJCAlignmentResult<R>> {
    protected volatile boolean initialized = false;
    protected final VDJCAlignerParameters parameters;
    protected final EnumMap<GeneType, List<VDJCGene>> genesToAlign = new EnumMap<>(GeneType.class);
    protected final List<VDJCGene> usedAlleles = new ArrayList<>();
    protected VDJCAlignerEventListener listener = null;

    protected VDJCAligner(VDJCAlignerParameters parameters) {
        this.parameters = parameters.clone();
        for (GeneType geneType : GeneType.values())
            genesToAlign.put(geneType, new ArrayList<VDJCGene>());
    }

    private static <R extends SequenceRead> long hash(R input) {
        long hash = 1;
        for (int i = 0; i < input.numberOfReads(); i++) {
            final SingleRead r = input.getRead(i);
            hash = 31 * hash + r.getData().getSequence().hashCode();
            if (r.getDescription() != null)
                hash = 31 * hash + r.getDescription().hashCode();
            else
                hash = 31 * hash + HashFunctions.JenkinWang64shift(input.getId());
        }
        return hash;
    }

    @Override
    public final VDJCAlignmentResult<R> process(R input) {
        if (parameters.isFixSeed())
            RandomUtil.reseedThreadLocal(hash(input));
        return process0(input);
    }

    protected abstract VDJCAlignmentResult<R> process0(final R input);

    public void setEventsListener(VDJCAlignerEventListener listener) {
        this.listener = listener;
    }

    protected final void onFailedAlignment(SequenceRead read, VDJCAlignmentFailCause cause) {
        if (listener != null)
            listener.onFailedAlignment(read, cause);
    }

    protected final void onSuccessfulAlignment(SequenceRead read, VDJCAlignments alignment) {
        if (listener != null)
            listener.onSuccessfulAlignment(read, alignment);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public final void ensureInitialized() {
        if (!initialized)
            synchronized (this) {
                if (!initialized) {
                    init();
                    initialized = true;
                }
            }
    }

    protected abstract void init();

    public VDJCAlignerParameters getParameters() {
        return parameters.clone();
    }

    public List<VDJCGene> getUsedGenes() {
        return Collections.unmodifiableList(usedAlleles);
    }

    public int addGene(VDJCGene allele) {
        usedAlleles.add(allele);
        List<VDJCGene> alleles = genesToAlign.get(allele.getGeneType());
        alleles.add(allele);
        return alleles.size() - 1;
    }

    public VDJCGene getGene(GeneType type, int index) {
        return genesToAlign.get(type).get(index);
    }

    public List<VDJCGene> getVAllelesToAlign() {
        return genesToAlign.get(GeneType.Variable);
    }

    public List<VDJCGene> getDAllelesToAlign() {
        return genesToAlign.get(GeneType.Diversity);
    }

    public List<VDJCGene> getJAllelesToAlign() {
        return genesToAlign.get(GeneType.Joining);
    }

    public List<VDJCGene> getCAllelesToAlign() {
        return genesToAlign.get(GeneType.Constant);
    }

    public static VDJCAligner createAligner(VDJCAlignerParameters alignerParameters,
                                            boolean paired, boolean merge) {
        return paired ?
                merge ? new VDJCAlignerWithMerge(alignerParameters)
                        : new VDJCAlignerPVFirst(alignerParameters)
                : new VDJCAlignerS(alignerParameters);
    }

    public static Chains getPossibleDLoci(VDJCHit[] vHits, VDJCHit[] jHits) {
        Chains chains = new Chains();
        for (VDJCHit h : vHits)
            chains = chains.merge(h.getGene().getChains());
        for (VDJCHit h : jHits)
            chains = chains.merge(h.getGene().getChains());
        return chains;
    }
}
