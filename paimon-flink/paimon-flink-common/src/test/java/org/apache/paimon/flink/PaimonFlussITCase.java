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

package org.apache.paimon.flink;

import org.apache.flink.types.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

/** sd. */
public class PaimonFlussITCase extends CatalogITCaseBase {

    @Test
    void testInsert() {

        tEnv.executeSql(
                "create catalog fluss with ("
                        + "'type' = 'paimon',"
                        + "'warehouse' = '"
                        + "/tmp/paimon-fluss-1"
                        + "', "
                        + "'streamstore' = 'fluss',"
                        + "'fluss.bootstrap.servers' = '127.0.0.1:55839'"
                        + ")");
        tEnv.useCatalog("fluss");

        sql("create table t2(a int, b string)");

        sql("alter table t2 set ('streamstore.enabled' = 'true') ");

        batchSql("INSERT INTO %s VALUES (1, '1'), (2, '2')", "t2");

        System.out.println("start to select ....");
        sEnv.executeSql(
                "create catalog fluss with ("
                        + "'type' = 'paimon',"
                        + "'warehouse' = '"
                        + "/tmp/paimon-fluss-1"
                        + "', "
                        + "'streamstore' = 'fluss',"
                        + "'fluss.bootstrap.servers' = '127.0.0.1:55839'"
                        + ")");
        sEnv.useCatalog("fluss");
        sEnv.executeSql("select * from t2")
                .print();
    }
}
