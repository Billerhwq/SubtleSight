package com.subtlesight.signal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Small safe expression parser; user expressions are never executed as SQL. */
public final class SavedViewParser {
    public sealed interface Node permits Predicate,And,Or,Not{}
    public record Predicate(String field,String operator,String value) implements Node{}
    public record And(Node left,Node right) implements Node{}
    public record Or(Node left,Node right) implements Node{}
    public record Not(Node child) implements Node{}
    private static final Set<String> FIELDS=Set.of("topic","entity","source","tier","language","score","age","status");
    private List<String> tokens;private int cursor;
    public Node parse(String expression){if(expression==null||expression.isBlank())throw new IllegalArgumentException("view expression is blank");tokens=tokenize(expression);cursor=0;Node result=or();if(cursor!=tokens.size())throw error("unexpected token");return result;}
    private Node or(){Node n=and();while(match("OR"))n=new Or(n,and());return n;}
    private Node and(){Node n=unary();while(match("AND"))n=new And(n,unary());return n;}
    private Node unary(){if(match("NOT"))return new Not(unary());if(match("(")){Node n=or();expect(")");return n;}return predicate();}
    private Node predicate(){String token=next();int p=firstOperator(token);if(p<1)throw error("predicate must be field:value");String field=token.substring(0,p).toLowerCase(Locale.ROOT);if(!FIELDS.contains(field))throw error("unsupported field "+field);String op=operatorAt(token,p);String value=token.substring(p+op.length());if(value.isBlank())throw error("empty value");return new Predicate(field,op,strip(value));}
    private int firstOperator(String t){int result=-1;for(String op:new String[]{">=","<=","!=",":",">","<","="}){int i=t.indexOf(op);if(i>=0&&(result<0||i<result))result=i;}return result;}
    private String operatorAt(String t,int p){for(String op:new String[]{">=","<=","!=",":",">","<","="})if(t.startsWith(op,p))return op;throw error("operator");}
    private List<String> tokenize(String s){List<String> out=new ArrayList<>();StringBuilder b=new StringBuilder();boolean quote=false;for(char c:s.toCharArray()){if(c=='\"'){quote=!quote;b.append(c);}else if(!quote&&(Character.isWhitespace(c)||c=='('||c==')')){if(!b.isEmpty()){out.add(b.toString());b.setLength(0);}if(c=='('||c==')')out.add(String.valueOf(c));}else b.append(c);}if(quote)throw new IllegalArgumentException("unterminated quote");if(!b.isEmpty())out.add(b.toString());return out;}
    private boolean match(String t){if(cursor<tokens.size()&&tokens.get(cursor).equalsIgnoreCase(t)){cursor++;return true;}return false;}
    private void expect(String t){if(!match(t))throw error("expected "+t);}
    private String next(){if(cursor>=tokens.size())throw error("unexpected end");return tokens.get(cursor++);}
    private String strip(String v){return v.length()>=2&&v.startsWith("\"")&&v.endsWith("\"")?v.substring(1,v.length()-1):v;}
    private IllegalArgumentException error(String m){return new IllegalArgumentException(m+" at token "+cursor);}
}

