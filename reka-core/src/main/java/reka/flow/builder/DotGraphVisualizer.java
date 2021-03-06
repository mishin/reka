package reka.flow.builder;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import reka.data.Data;
import reka.flow.builder.FlowVisualizer.GraphVisualizer;
import reka.flow.builder.FlowVisualizer.NodeType;
import reka.util.Path;

public class DotGraphVisualizer implements GraphVisualizer<String> {
	
	private static class Connection {
		final int from, to;
		final String name;
		final boolean optional;
		Connection(int from, int to, String name, boolean optional) {
			this.from = from; this.to = to; this.name = name; this.optional = optional;
		} 
	}
	
	private final Map<Integer,String> nodes = new HashMap<>();
	private final Map<Integer,NodeType> nodeTypes = new HashMap<>();
	private final Map<Path,List<Integer>> groups = new TreeMap<>(); // sorted
	private final List<DotGraphVisualizer.Connection> connections = new ArrayList<>();

	private final StringBuilder sb = new StringBuilder();
	private final String font = "arial";
	
	@Override
	public void node(int id, String name, NodeType type) {
		nodes.put(id, name);
		nodeTypes.put(id, type);
	}
	
	@Override
	public void group(Path path, int id) {
		
		if (!groups.containsKey(path)) {
			List<Integer> list = new ArrayList<>();
			groups.put(path, list);
		}
		groups.get(path).add(id);
	}

	@Override
	public void connect(int from, int to, String label, boolean optional) {
		connections.add(new Connection(from, to, label != null ? label : null, optional));
	}
	
	private String idToText(int id) {
	    String name = nodes.get(id);
	    return name != null ? name : format("%d", id);
	}
	
	@Override
	public String build() {
		sb.append("digraph G {\n");
		sb.append("  fontname = \"arial\"\n");
		sb.append("  edge [fontsize=11]\n");
		
		List<Entry<Integer,String>> sortedNodes = new ArrayList<>(nodes.entrySet());
		
		sortedNodes.sort((a, b) -> idToText(a.getKey()).compareTo(idToText(b.getKey())));
		
		for (Entry<Integer, String> node : sortedNodes) {
			String shape;
			NodeType type = nodeTypes.get(node.getKey());
			
			// http://www.graphviz.org/doc/info/shapes.html
			switch (type) {
			case START:
			case END:
				shape = "circle";
				break;
			default:
				shape = "box";
				break;
			}
			sb.append(format("  %s [id=\"%s\", shape=%s, penwidth=\"0\", fontname=%s, style=\"filled\", color=\"#ddddff\" fillcolor=\"#ddddff\", label=%s]\n",
			    quote(String.valueOf(node.getKey())), 
				format("node__%s__%s__", node.getKey(), type),
				shape,
			    quote(font), 
			    quoteUnlessHTML(format("%s", idToText(node.getKey())))));
		}
		
		for (DotGraphVisualizer.Connection connection : connections) {
			String label = labelFor(connection);
			String formattedLabel = label == null ? "" : format(", label=%s", quote(label));
			sb.append(format("  %s -> %s [id=%s, fontname=%s, fonsize=8, color=\"#888888\", style=%s%s, arrowsize=.5]\n", 
				quote(String.valueOf(connection.from)), 
				quote(String.valueOf(connection.to)),  
				quote(format("connection__%s__%s", connection.from, connection.to)),
				quote(font), 
				connection.optional ? "dashed" : "solid", formattedLabel));
		}
		
		int id = 0;
		Path last = null;
		for (Entry<Path, List<Integer>> entry : groups.entrySet()) {
			Path path = entry.getKey();
			
			String indent = indent(path.length());
			
			if (last != null) {
				if (path.startsWith(last)) {
					// nested
				} else {
					for (int i = last.length(); i > 0; i--) {
						// keep adding closing } until we have a common path
						if (path.startsWith(last.subpath(0, i))) break;
						sb.append(format("%s}\n", indent(i)));
					}
				}
			}
			
			sb.append(format("%ssubgraph cluster_%d {\n", indent, ++id));
			sb.append(format("%s  graph[style=dashed, color=\"#dddddd\"]\n", indent));
			sb.append(format("%s  labeljust=\"l\"\n", indent));
			sb.append(format("%s  label = \"%s\"\n", indent, path.last()));
			
			for (int node : entry.getValue()) {
				sb.append(format("%s  %s\n", indent, quote(String.valueOf(node))));
			}
			
			last = entry.getKey();
		}
		
		if (last != null) {
			for (int i = last.length(); i > 0; i--) {
				sb.append(format("%s}\n", indent(i)));
			}
		}
		
		sb.append("}\n");
		return sb.toString();
	}
	
	private String labelFor(Connection connection) {
		if (connection.name != null && !connection.name.isEmpty()) {
			return connection.name;
		} else {
			return null;
		}
	}

	private String indent(int depth) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; i++) {
			sb.append("  ");
		}
		return sb.toString();
	}
		
	private String quote(String value) {
		return format("\"%s\"", value);
	}
	
	private String quoteUnlessHTML(String value) {
	    return value.startsWith("<") && value.endsWith(">") ? value : quote(value);
	}

	@Override
	public void meta(int id, List<Data> metas) {
		
	}
	
}