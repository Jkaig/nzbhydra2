/*
 *  (C) Copyright 2017 TheOtherP (theotherp@gmx.de)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.nzbhydra.indexers;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.nzbhydra.config.ConfigProvider;
import org.nzbhydra.config.indexer.IndexerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IndexerStatuses {

    @Autowired
    private ConfigProvider configProvider;

    public List<IndexerStatus> getSortedStatuses() {
        return configProvider.getBaseConfig().getIndexers().stream()
                .sorted(
                        Comparator.comparing(IndexerConfig::getState)
                                .thenComparing(o -> o.getName().toLowerCase())
                )
                .map(
                        x -> new IndexerStatus(
                                x.getName(),
                                x.getState().name(),
                                x.getDisabledLevel(),
                                (x.getDisabledUntil() == null ? null : Instant.ofEpochMilli(x.getDisabledUntil())),
                                x.getLastError()
                        )
                )
                .collect(Collectors.toList());
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IndexerStatus {
        private String indexer;
        private String state;
        private int level;
        private Instant disabledUntil;
        private String lastError;

    }
}
