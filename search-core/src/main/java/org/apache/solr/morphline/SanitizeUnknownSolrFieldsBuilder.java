/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.solr.morphline;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.apache.solr.schema.IndexSchema;

import com.cloudera.cdk.morphline.api.Command;
import com.cloudera.cdk.morphline.api.CommandBuilder;
import com.cloudera.cdk.morphline.api.Configs;
import com.cloudera.cdk.morphline.api.MorphlineContext;
import com.cloudera.cdk.morphline.api.Record;
import com.cloudera.cdk.morphline.base.AbstractCommand;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.typesafe.config.Config;

/**
 * Command that sanitizes record fields that are unknown to Solr schema.xml by either deleting them
 * (renameToPrefix is absent or a zero length string), or by moving them to a field prefixed with
 * the given renameToPrefix (e.g. renameToPrefix = "ignored_" to use typical dynamic Solr fields).
 * <p>
 * Recall that Solr throws an exception on any attempt to load a document that contains a field that
 * isn't specified in schema.xml.
 */
public final class SanitizeUnknownSolrFieldsBuilder implements CommandBuilder {

  @Override
  public Collection<String> getNames() {
    return Collections.singletonList("sanitizeUnknownSolrFields");
  }

  @Override
  public Command build(Config config, Command parent, Command child, MorphlineContext context) {
    return new SanitizeUnknownSolrFields(config, parent, child, context);
  }
  
  
  ///////////////////////////////////////////////////////////////////////////////
  // Nested classes:
  ///////////////////////////////////////////////////////////////////////////////
  private static final class SanitizeUnknownSolrFields extends AbstractCommand {
    
    private final IndexSchema schema;
    private final String renameToPrefix;
        
    public SanitizeUnknownSolrFields(Config config, Command parent, Command child, MorphlineContext context) {
      super(config, parent, child, context);      
      
      Config solrLocatorConfig = Configs.getConfig(config, "solrLocator");
      SolrLocator locator = new SolrLocator(solrLocatorConfig, context);
      LOG.debug("solrLocator: {}", locator);
      this.schema = locator.getIndexSchema();
      Preconditions.checkNotNull(schema);
      LOG.trace("Solr schema: \n{}", Joiner.on("\n").join(new TreeMap(schema.getFields()).values()));
      
      String str = Configs.getString(config, "renameToPrefix", "").trim();
      this.renameToPrefix = str.length() > 0 ? str : null;  
    }
    
    @Override
    public boolean process(Record record) {
      Collection<String> keys = new ArrayList<String>(record.getFields().keySet());
      for (String key : keys) {
        if (schema.getFieldOrNull(key) == null) {
          LOG.debug("Sanitizing unknown Solr field: {}", key);
          if (renameToPrefix != null) {
            List values = record.getFields().get(key);
            record.getFields().putAll(renameToPrefix + key, values);
          }
          record.removeAll(key);
        }
      }
      return super.process(record);
    }
    
  }
}