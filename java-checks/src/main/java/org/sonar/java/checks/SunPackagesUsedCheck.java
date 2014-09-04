/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.checks;

import org.sonar.api.rule.RuleKey;
import org.sonar.check.BelongsToProfile;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.java.model.JavaTree;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

@Rule(
    key = SunPackagesUsedCheck.RULE_KEY,
    priority = Priority.MAJOR)
@BelongsToProfile(title = "Sonar way", priority = Priority.MAJOR)
public class SunPackagesUsedCheck extends BaseTreeVisitor implements JavaFileScanner {

  public static final String RULE_KEY = "S1191";
  private Set<Integer> reportedLines = new HashSet<Integer>();

  private static final String DEFAULT_EXCLUDE = "";

  @RuleProperty(
      key = "exclude",
      defaultValue = "" + DEFAULT_EXCLUDE)
  public String exclude = DEFAULT_EXCLUDE;
  private String[] excludePackages = null;
  private JavaFileScannerContext context;
  private RuleKey ruleKey = RuleKey.of(CheckList.REPOSITORY_KEY, RULE_KEY);

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    reportedLines.clear();
    excludePackages = exclude.split(",");
    scan(context.getTree());
  }

  @Override
  public void visitMemberSelectExpression(MemberSelectExpressionTree tree) {
    String reference = merge(tree);
    if (!isExcluded(reference)) {
      int line = ((JavaTree) tree).getLine();
      if (!reportedLines.contains(line) && isSunClass(reference)) {
        context.addIssue(line, ruleKey, "Replace this usage of Sun classes by ones from the Java API.");
        reportedLines.add(line);
      }
      super.visitMemberSelectExpression(tree);
    }
  }

  private boolean isSunClass(String reference) {
    return "com.sun".equals(reference) || reference.matches("sun\\.[^\\.]*");
  }

  private String merge(ExpressionTree tree) {
    Deque<String> pieces = new LinkedList<String>();
    ExpressionTree expr = tree;
    while (expr.is(Tree.Kind.MEMBER_SELECT)) {
      MemberSelectExpressionTree mse = (MemberSelectExpressionTree) expr;
      pieces.push(mse.identifier().name());
      pieces.push(".");
      expr = mse.expression();
    }
    if (expr.is(Tree.Kind.IDENTIFIER)) {
      IdentifierTree idt = (IdentifierTree) expr;
      pieces.push(idt.name());
    }

    StringBuilder sb = new StringBuilder();
    for (String piece : pieces) {
      sb.append(piece);
    }
    return sb.toString();
  }

  private boolean isExcluded(String reference) {
    for (String str : excludePackages) {
      if (!str.isEmpty() && reference.startsWith(str)) {
        return true;
      }
    }
    return false;
  }
}
