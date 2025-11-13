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
    void testInsert() throws Exception {
        sql("CREATE TABLE t1 (a INT, b STRING) with ('streaming-store' = 'fluss', 'fluss.bootstrap.servers' = 'localhost:9123')");
        batchSql("INSERT INTO %s VALUES (1, '1'), (2, '2')", "t1");

        List<Row> rows =
        batchSql("select * from t1");
    }

}
