package com.suushiemaniac.cubing.bld.verify;

import com.suushiemaniac.cubing.alglib.alg.SubGroup;
import com.suushiemaniac.cubing.alglib.lang.NotationReader;
import com.suushiemaniac.cubing.alglib.util.ParseUtils;
import com.suushiemaniac.cubing.bld.analyze.cube.FiveBldCube;
import com.suushiemaniac.cubing.bld.enumeration.CubicPieceType;
import com.suushiemaniac.cubing.bld.util.BruteForceUtil;
import com.suushiemaniac.cubing.bld.util.StringUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Verificator {
    protected static String[] fullLetterPairs = BruteForceUtil.genBlockString(BruteForceUtil.ALPHABET, 2, false);

    private NotationReader parser;

    protected Verificator(NotationReader parser) {
        this.parser = parser;
    }

    public Map<String, Map<String, Boolean>> verifyAll(CubicPieceType type) {
        Map<String, Map<String, Boolean>> fullSolutionMap = new HashMap<>();
        for (String possPair : fullLetterPairs) fullSolutionMap.put(possPair, this.verifySingleCase(type, possPair));
        return fullSolutionMap;
    }

    public Map<String, Boolean> verifySingleCase(CubicPieceType type, String letterPair) {
        Map<String, Boolean> solutionMap = new HashMap<>();
        List<String> algStringList = this.getAlgStrings(type, letterPair);
        if (algStringList != null)
            for (String alg : algStringList)
                solutionMap.put(alg, ParseUtils.isParseable(alg, this.parser) && FiveBldCube.solves(type, this.parser.parse(alg).plain().toFormatString(), letterPair));
        return solutionMap;
    }

    public Map<String, List<String>> findMatchingSubGroup(CubicPieceType type, SubGroup group) {
        Map<String, List<String>> sameGroupMap = new HashMap<>();
        for (String possPair : fullLetterPairs) {
            sameGroupMap.put(possPair, new ArrayList<>());
            List<String> algStringList = this.getAlgStrings(type, possPair);
            if (algStringList != null)
                for (String alg : algStringList)
                    if (ParseUtils.isParseable(alg, this.parser) && this.parser.parse(alg).getSubGroup().sameOrLargerSubGroup(group))
                        sameGroupMap.get(possPair).add(alg);
        }
        return sameGroupMap;
    }

    public Map<String, List<String>> checkParseable(CubicPieceType type) {
        Map<String, List<String>> unparseableMap = new HashMap<>();
        for (String possPair : fullLetterPairs) {
            unparseableMap.put(possPair, new ArrayList<>());
            List<String> algStringList = this.getAlgStrings(type, possPair);
            if (algStringList != null)
                for (String alg : algStringList)
                    if (!ParseUtils.isParseable(alg, this.parser))
                        unparseableMap.get(possPair).add(alg);
        }
        return unparseableMap;
    }

    protected abstract List<String> getAlgStrings(CubicPieceType type, String letterPair);
}