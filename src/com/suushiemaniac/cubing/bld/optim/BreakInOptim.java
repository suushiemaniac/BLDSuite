package com.suushiemaniac.cubing.bld.optim;

import com.suushiemaniac.cubing.alglib.alg.Algorithm;
import com.suushiemaniac.cubing.bld.model.AlgSource;
import com.suushiemaniac.cubing.bld.model.enumeration.PieceType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BreakInOptim {
    private AlgSource source;

    public BreakInOptim(AlgSource source) {
        this.source = source;
    }

    public List<Algorithm> optimizeBreakInsAfter(char target, PieceType type) {
        List<Algorithm> algList = new ArrayList<>();
        for (char c = 'A'; c < 'Y'; c++) {
            List<Algorithm> sourceList = this.source.getAlg(type, ("" + target) + c);
            if (sourceList == null) continue;
            algList.addAll(sourceList);
        }
        Collections.sort(algList, (o1, o2) -> o1.getSubGroup().toFormatString().compareTo(o2.getSubGroup().toFormatString()));
        Collections.sort(algList, (o1, o2) -> Integer.compare(o1.getSubGroup().size(), o2.getSubGroup().size()));
        Collections.sort(algList, (o1, o2) -> Integer.compare(o1.length(), o2.length()));
        return algList;
    }
}
