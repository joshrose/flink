/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.partition.hybrid.tiered.tier.disk;

import org.apache.flink.runtime.io.network.buffer.Buffer;
import org.apache.flink.runtime.io.network.partition.ResultSubpartitionIndexSet;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.common.TieredStorageInputChannelId;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.common.TieredStoragePartitionId;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.common.TieredStorageSubpartitionId;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.netty.NettyConnectionReader;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.netty.TieredStorageNettyService;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.storage.AvailabilityNotifier;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.storage.TieredStorageConsumerSpec;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.storage.TieredStorageMemoryManager;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.tier.TierConsumerAgent;
import org.apache.flink.runtime.io.network.partition.hybrid.tiered.tier.TierShuffleDescriptor;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** The data client is used to fetch data from disk tier. */
public class DiskTierConsumerAgent implements TierConsumerAgent {

    private final Map<
                    TieredStoragePartitionId,
                    Map<TieredStorageSubpartitionId, CompletableFuture<NettyConnectionReader>>>
            nettyConnectionReaders = new HashMap<>();

    public DiskTierConsumerAgent(
            List<TieredStorageConsumerSpec> tieredStorageConsumerSpecs,
            TieredStorageNettyService nettyService) {
        for (TieredStorageConsumerSpec tieredStorageConsumerSpec : tieredStorageConsumerSpecs) {
            TieredStoragePartitionId partitionId = tieredStorageConsumerSpec.getPartitionId();
            for (int subpartitionId : tieredStorageConsumerSpec.getSubpartitionIds().values()) {
                nettyConnectionReaders
                        .computeIfAbsent(partitionId, ignore -> new HashMap<>())
                        .put(
                                new TieredStorageSubpartitionId(subpartitionId),
                                nettyService.registerConsumer(
                                        partitionId,
                                        new TieredStorageSubpartitionId(subpartitionId)));
            }
        }
    }

    @Override
    public void setup(TieredStorageMemoryManager memoryManager) {
        // noop
    }

    @Override
    public void start() {
        // noop
    }

    @Override
    public void registerAvailabilityNotifier(AvailabilityNotifier notifier) {
        // noop
    }

    @Override
    public void updateTierShuffleDescriptor(
            TieredStoragePartitionId partitionId,
            TieredStorageInputChannelId inputChannelId,
            TieredStorageSubpartitionId subpartitionId,
            TierShuffleDescriptor tierShuffleDescriptor) {
        // noop
    }

    @Override
    public int peekNextBufferSubpartitionId(
            TieredStoragePartitionId partitionId, ResultSubpartitionIndexSet indexSet)
            throws IOException {
        for (CompletableFuture<NettyConnectionReader> readerFuture :
                nettyConnectionReaders.get(partitionId).values()) {
            int subpartitionId;
            try {
                subpartitionId = readerFuture.get().peekNextBufferSubpartitionId();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to peek subpartition Id.", e);
            }
            if (indexSet.contains(subpartitionId)) {
                return subpartitionId;
            }
        }
        return -1;
    }

    @Override
    public Optional<Buffer> getNextBuffer(
            TieredStoragePartitionId partitionId,
            TieredStorageSubpartitionId subpartitionId,
            int segmentId) {
        try {
            return nettyConnectionReaders
                    .get(partitionId)
                    .get(subpartitionId)
                    .get()
                    .readBuffer(subpartitionId.getSubpartitionId(), segmentId);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get next buffer.", e);
        }
    }

    @Override
    public void close() throws IOException {
        // noop
    }
}
