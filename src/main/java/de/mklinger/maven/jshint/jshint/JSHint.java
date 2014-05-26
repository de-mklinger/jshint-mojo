package de.mklinger.maven.jshint.jshint;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.EcmaError;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;

import de.mklinger.maven.jshint.util.Rhino;

public class JSHint {

    private final Rhino rhino;

    public JSHint(final String jshintCode) {

        rhino = new Rhino();
        try {
            rhino.eval(
            		"print=function(){};" +
            		"quit=function(){};" +
            		"readFile=function(){};" +
            		"arguments=[];");

            rhino.eval(commentOutTheShebang(resourceAsString(jshintCode)));
        } catch (EcmaError e) {
            throw new RuntimeException("Javascript eval error:" + e.getScriptStackTrace(), e);
        }
    }

    private String commentOutTheShebang(final String code) {
        String minusShebang = code.startsWith("#!")?"//" + code : code;
        return minusShebang;
    }

    public List<Hint> run(final InputStream source, final String options, final String globals) {
        final List<Hint> results = new ArrayList<JSHint.Hint>();

        String sourceAsText = toString(source);

        NativeObject nativeOptions = toJsObject(options);
        NativeObject nativeGlobals = toJsObject(globals);

        Boolean codePassesMuster = rhino.call("JSHINT", sourceAsText, nativeOptions, nativeGlobals);

        if(!codePassesMuster){
            NativeArray errors = rhino.eval("JSHINT.errors");

            for(Object next : errors){
                if(next != null){ // sometimes it seems that the last error in the list is null
                    JSObject jso = new JSObject(next);
                    // the if will exclude the "too many errors" error. But I want to see it.
//                    if ("(error)".equals((String)jso.dot("id"))) {
                        results.add(new Hint(jso));
//                    }
                }
            }
        }

        return results;
    }

    private NativeObject toJsObject(final String options) {
        NativeObject nativeOptions = new NativeObject();
        for (final String nextOption : options.split(",")) {
            final String option = nextOption.trim();
            if(!option.isEmpty()){
                final String name;
                final Object value;

                final int valueDelimiter = option.indexOf(':');
                if(valueDelimiter==-1){
                    name = option;
                    value = Boolean.TRUE;
                } else {
                    name = option.substring(0, valueDelimiter);
                    String rest = option.substring(valueDelimiter+1).trim();
                    if (rest.matches("[0-9]+")) {
                        value = Integer.parseInt(rest);
                    } else if (rest.equals("true")) {
                        value = Boolean.TRUE;
                    } else if (rest.equals("false")) {
                        value = Boolean.FALSE;
                    } else {
                        value = rest;
                    }
                }
                nativeOptions.defineProperty(name, value, ScriptableObject.READONLY);
            }
        }
        return nativeOptions;
    }

    private static String toString(final InputStream in) {
        try {
            return IOUtils.toString(in);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String resourceAsString(final String name) {
        return toString(getClass().getResourceAsStream(name));
    }

    @SuppressWarnings("unchecked")
    static class JSObject {
        private final NativeObject a;

        public JSObject(final Object o) {
            if(o==null) {
                throw new NullPointerException();
            }
            this.a = (NativeObject)o;
        }

        public <T> T dot(final String name){
            return (T) a.get(name);
        }
    }

    public static class Hint implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public HintSeverity severity;
        public String id, code, raw, evidence, reason;
        public Number line, character;

        public Hint(final JSObject o) {
            id = o.dot("id");
            code = o.dot("code");
            raw = o.dot("raw");
            evidence = o.dot("evidence");
            line = o.dot("line");
            character = o.dot("character");
            reason = o.dot("reason");
            char c = code.charAt(0);
            switch (c) {
                case 'E':
                    severity = HintSeverity.ERROR;
                    break;
                case 'W':
                    severity = HintSeverity.WARNING;
                    break;
                case 'I':
                    severity = HintSeverity.INFO;
                    break;
                default:
                    throw new IllegalArgumentException("Unexpected char c=" + c);
            }
        }

        public Hint() { }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Hint [severity=");
            builder.append(severity);
            builder.append(", id=");
            builder.append(id);
            builder.append(", code=");
            builder.append(code);
            builder.append(", raw=");
            builder.append(raw);
            builder.append(", evidence=");
            builder.append(evidence);
            builder.append(", reason=");
            builder.append(reason);
            builder.append(", line=");
            builder.append(line);
            builder.append(", character=");
            builder.append(character);
            builder.append("]");
            return builder.toString();
        }
    }

    public static enum HintSeverity {
        ERROR,
        WARNING,
        INFO
    }
}