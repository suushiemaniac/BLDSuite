package com.suushiemaniac.cubing.bld.filter;

import com.suushiemaniac.cubing.alglib.alg.Algorithm;
import com.suushiemaniac.cubing.alglib.lang.CubicAlgorithmReader;
import com.suushiemaniac.cubing.alglib.lang.NotationReader;
import com.suushiemaniac.cubing.bld.analyze.cube.BldCube;
import net.gnehzr.tnoodle.scrambles.Puzzle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public abstract class BldScramble {
    public static final String REGEX_UNIV = ".*";

    public String findScrambleOnThread() {
        BldCube testCube = this.getAnalyzingPuzzle();
        Puzzle tNoodle = this.getScramblingPuzzle();
        NotationReader reader = new CubicAlgorithmReader();

        String scramble;
        do {
            scramble = tNoodle.generateScramble();
            testCube.parseScramble(reader.parse(scramble));
        } while (!this.matchingConditions(testCube));

        return scramble;
    }

    public List<String> findScramblesOnThread(int num) {
        List<String> scrambles = new ArrayList<>();

        for (int i = 0; i < num; i++) {
            if (i % (num / Math.min(100, num)) == 0) {
                System.out.println(i);
            }

            scrambles.add(this.findScrambleOnThread());
        }

        return scrambles;
    }

    public void findScrambleThreadModel(int numScrambles, int numThreads) {
        BlockingQueue<Algorithm> scrambleQueue = new ArrayBlockingQueue<>(50);
        ScrambleProducer producer = new ScrambleProducer(scrambleQueue);
        ScrambleConsumer consumer = new ScrambleConsumer(numScrambles, scrambleQueue);

        for (int i = 0; i < numThreads; i++) {
            Thread genThread = new Thread(producer, "Producer " + (i + 1));
            genThread.setDaemon(true);
            genThread.start();
        }

        new Thread(consumer, "Consumer").start();
    }

    protected final class ScrambleProducer implements Runnable {
        private final Puzzle puzzle;
		private final NotationReader reader;
        private final BlockingQueue<Algorithm> scrambleQueue;

        public ScrambleProducer(BlockingQueue<Algorithm> queue) {
            this.puzzle = BldScramble.this.getScramblingPuzzle();
			this.reader = new CubicAlgorithmReader();
            this.scrambleQueue = queue;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    this.scrambleQueue.put(this.reader.parse(this.puzzle.generateScramble()));
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    protected final class ScrambleConsumer implements Runnable {
        private final int numScrambles;
        private int readScrambles;
        private final BldCube testCube;
        private final BlockingQueue<Algorithm> scrambleQueue;

        public ScrambleConsumer(int numScrambles, BlockingQueue<Algorithm> queue) {
            this.numScrambles = numScrambles;
            this.readScrambles = 0;
            this.testCube = BldScramble.this.getAnalyzingPuzzle();
            this.scrambleQueue = queue;
        }

        @Override
        public void run() {
            int count = 0;
            do {
                try {
                    this.testCube.parseScramble(this.scrambleQueue.take());
                    this.readScrambles++;

                    if (matchingConditions(this.testCube)) {
                        String header = (this.numScrambles > 1 ? (count + 1) + "\t" : "");
                        String findCount = this.readScrambles + "\t\t";

                        System.out.println(header + findCount + this.testCube.getScramble().toFormatString());

                        this.readScrambles = 0;
                        count++;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            } while (count < this.numScrambles);
        }
    }

    protected abstract <T extends BldCube> boolean matchingConditions(T inCube);

    protected abstract Puzzle getScramblingPuzzle();

    protected abstract BldCube getAnalyzingPuzzle();

    public void setSolvingOrientation(int top, int front) {
        this.getAnalyzingPuzzle().setSolvingOrientation(top, front);
    }

    protected static int getNumInStatArray(int[] stat, int pos, int offset, int scale) {
        int mem = 0;
        for (int i = 0; i < stat.length; i++) {
            mem += stat[i];
            if (pos < mem)
                return offset + scale * i;
        }
        return 0;
    }

    protected static int getNumInStatArray(int[] stat, int pos) {
        return getNumInStatArray(stat, pos, 0, 1);
    }
}