/*
 * LensKit, an open source recommender systems toolkit.
 * Copyright 2010-2012 Regents of the University of Minnesota and contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.grouplens.lenskit.knn.item;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

import org.grouplens.lenskit.collections.ScoredLongList;
import org.grouplens.lenskit.transform.threshold.Threshold;
import org.grouplens.lenskit.util.ScoredItemAccumulator;
import org.grouplens.lenskit.util.TopNScoredItemAccumulator;
import org.grouplens.lenskit.util.UnlimitedScoredItemAccumulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accumulator for item similarities that go into the item-item CF model.
 *
 * @author Michael Ekstrand <ekstrand@cs.umn.edu>
 */
public class SimilarityMatrixAccumulator {
    private static final Logger logger = LoggerFactory.getLogger(SimilarityMatrixAccumulator.class);

    private final Threshold threshold;
    private Long2ObjectMap<ScoredItemAccumulator> rows;
    private LongSortedSet itemUniverse;

    public SimilarityMatrixAccumulator(int modelSize, LongSortedSet entities, Threshold threshold) {
        logger.debug("Using model size of {} for {} items", modelSize, entities.size());
        this.threshold = threshold;
        itemUniverse = entities;

        rows = new Long2ObjectOpenHashMap<ScoredItemAccumulator>(entities.size());
        LongIterator it = entities.iterator();
        while (it.hasNext()) {
            if (modelSize == 0) {
                rows.put(it.nextLong(), new UnlimitedScoredItemAccumulator());
            } else {
                rows.put(it.nextLong(), new TopNScoredItemAccumulator(modelSize));
            }

        }
    }

    /**
     * Store an entry in the similarity matrix.
     * @param i The matrix row (an item ID).
     * @param j The matrix column (an item ID).
     * @param sim The similarity between items {@code j} and {@code i}. As documented in the
     *            {@link org.grouplens.lenskit.knn.item package docs}, this is \(s(j,i)\).
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public void put(long i, long j, double sim) {
        Preconditions.checkState(rows != null, "model already built");

        if (!threshold.retain(sim)) return;

        // concurrent read-only array access permitted
        ScoredItemAccumulator q = rows.get(i);
        // synchronize on this row to add item
        synchronized (q) {
            q.put(j, sim);
        }
    }

    /**
     * Moves the result matrix into a SimilarityMatrixModel.
     * @return The resulting SimilarityMatrixModel.
     */
    public SimilarityMatrixModel build() {
        Long2ObjectMap<ScoredLongList> data = new Long2ObjectOpenHashMap<ScoredLongList>(rows.size());
        for (Entry<ScoredItemAccumulator> row: rows.long2ObjectEntrySet()) {
            data.put(row.getLongKey(), row.getValue().finish());
        }
        SimilarityMatrixModel model = new SimilarityMatrixModel(itemUniverse, data);
        rows = null;
        return model;
    }

}