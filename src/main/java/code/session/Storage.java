/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package code.session;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import code.session.request.RebalanceRequest;

public class Storage {
    private final Map<Integer, Map<String, String>> data = new ConcurrentHashMap<>();

    private final Mapper mapper;

    private final UUID localId;

    private final Communication comm;

    public Storage(Discovery discovery, UUID localId, Communication comm) {
        mapper = new Mapper(discovery);

        this.localId = localId;
        this.comm = comm;
    }

    public void put(String key, String value) {
        data.get(mapper.partition(key)).put(key, value);
    }

    public String get(String key) {
        return data.get(mapper.partition(key)).get(key);
    }

    public void remap() {
        Discovery.Node[] mapping = mapper.partitionMapping();

        for (int partition = 0; partition < mapping.length; partition++) {
            Discovery.Node node = mapping[partition];

            if (node.getId().equals(localId)) {
                data.putIfAbsent(partition, new ConcurrentHashMap<>());
            }
            else {
                Map<String, String> partitionData = data.remove(partition);

                if (partitionData != null) {
                    try {
                        comm.execute(new RebalanceRequest(partition, partitionData), node.getAddress());
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        System.out.println("Remapping completed.");
    }

    public void onPartitionReceived(int partition, Map<String, String> partitionData) {
        data.put(partition, partitionData);
    }
}
