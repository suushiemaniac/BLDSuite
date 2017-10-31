package com.suushiemaniac.cubing.bld.util;

import com.suushiemaniac.cubing.bld.model.enumeration.piece.LetterPairImage;
import com.suushiemaniac.cubing.bld.model.source.AlgSource;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class MemoUtil {
    public static List<String> genMemoTree(String pairs, AlgSource source, int lpiListSizeLimit) throws SQLException {
        List<String> treeList = new ArrayList<>(Collections.singletonList(""));

        for (String pair : pairs.split("(?<=\\G.{2})")) {
            if (pair.length() == 1) {
                List<String> oldMemoStrings = new ArrayList<>(treeList);

                for (String oldMemo : oldMemoStrings) {
                    treeList.add(oldMemo + (oldMemo.length() > 0 ? " // " : "") + "Parity: " + pair);
                }

                treeList.removeAll(oldMemoStrings);
            } else if (pair.length() == 2) {
                List<String> words = new ArrayList<>(source.getRawAlgorithms(LetterPairImage.NOUN, pair));
                words = words.subList(0, Math.min(lpiListSizeLimit, words.size()));
                List<String> oldMemoStrings = new ArrayList<>(treeList);

                for (String oldMemo : oldMemoStrings) {
                    for (String word : words) {
                        treeList.add(oldMemo + (oldMemo.length() > 0 ? " // " : "") + word);
                    }
                }

                treeList.removeAll(oldMemoStrings);
            }
        }

        return treeList;
    }

    public static List<String> genMemoTree(String pairs, AlgSource source) throws SQLException {
        return genMemoTree(pairs, source, Integer.MAX_VALUE);
    }
}
