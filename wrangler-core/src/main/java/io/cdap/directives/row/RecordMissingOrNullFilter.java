/*
 *  Copyright © 2017-2019 Cask Data, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *  use this file except in compliance with the License. You may obtain a copy of
 *  the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *  License for the specific language governing permissions and limitations under
 *  the License.
 */

package io.cdap.directives.row;

import io.cdap.cdap.api.annotation.Description;
import io.cdap.cdap.api.annotation.Name;
import io.cdap.cdap.api.annotation.Plugin;
import io.cdap.cdap.etl.api.relational.ExpressionFactory;
import io.cdap.cdap.etl.api.relational.InvalidRelation;
import io.cdap.cdap.etl.api.relational.Relation;
import io.cdap.cdap.etl.api.relational.RelationalTranformContext;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.annotations.Categories;
import io.cdap.wrangler.api.lineage.Lineage;
import io.cdap.wrangler.api.lineage.Mutation;
import io.cdap.wrangler.api.parser.ColumnNameList;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;
import io.cdap.wrangler.utils.SqlExpressionGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Filters records if they don't have all the columns specified or they have null values or combination.
 */
@Plugin(type = Directive.TYPE)
@Name(RecordMissingOrNullFilter.NAME)
@Categories(categories = { "row", "data-quality"})
@Description("Filters row that have empty or null columns.")
public class RecordMissingOrNullFilter implements Directive, Lineage {
  public static final String NAME = "filter-empty-or-null";
  private String[] columns;

  @Override
  public UsageDefinition define() {
    UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
    builder.define("column", TokenType.COLUMN_NAME_LIST);
    return builder.build();
  }

  @Override
  public void initialize(Arguments args) throws DirectiveParseException {
    List<String> cols = ((ColumnNameList) args.value("column")).value();
    columns = new String[cols.size()];
    columns = cols.toArray(columns);
  }

  @Override
  public void destroy() {
    // no-op
  }

  @Override
  public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
    List<Row> results = new ArrayList<>();
    for (Row row : rows) {
      boolean missingOrNull = true;
      for (String column : columns) {
        int idx = row.find(column.trim());
        if (idx != -1) {
          Object value = row.getValue(idx);
          if (value != null) {
            missingOrNull = false;
          }
        } else {
          results.add(row);
        }
      }
      if (!missingOrNull) {
        results.add(row);
      }
    }
    return results;
  }

  @Override
  public Mutation lineage() {
    List<String> cols = Arrays.asList(columns);
    Mutation.Builder builder = Mutation.builder()
      .readable("Filtered null or empty records based on check on columns '%s'", cols);
    cols.forEach(column -> builder.relation(column, column));
    return builder.build();
  }

  @Override
  public Relation transform(RelationalTranformContext relationalTranformContext,
                            Relation relation) {
    Optional<ExpressionFactory<String>> expressionFactory = SqlExpressionGenerator
            .getExpressionFactory(relationalTranformContext);
    if (!expressionFactory.isPresent()) {
      return new InvalidRelation("Cannot find an Expression Factory");
    }
    return relation.filter(expressionFactory.get().compile("nvl(" + columns[0] + ", false)"));
  }
}
