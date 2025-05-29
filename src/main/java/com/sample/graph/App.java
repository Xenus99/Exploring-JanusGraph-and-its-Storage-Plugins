package com.sample.graph;

import org.apache.commons.csv.*;
import org.janusgraph.core.*;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.apache.tinkerpop.gremlin.structure.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class App {

    public static void main(String[] args) {
        System.out.println("Starting JanusGraph In-Memory Example...");
        JanusGraph graph = JanusGraphFactory.build().set("storage.backend", "inmemory").open();
        System.out.println("JanusGraph instance created!");

        initializeSchema(graph);
        loadData(graph);

        graph.close();
        System.out.println("Graph loaded, schema initialized, and JanusGraph instance closed.");
    }

    private static void initializeSchema(JanusGraph graph) {
        System.out.println("Initializing schema...");
        JanusGraphManagement mgmt = graph.openManagement();

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(new File("air-routes-schema.json"));

            // Create Vertex Labels
            System.out.println("Creating vertex labels...");
            for (JsonNode vertexLabel : root.get("vertexLabels")) {
                String name = vertexLabel.get("name").asText();
                mgmt.makeVertexLabel(name).make();
            }

            // Create Edge Labels
            System.out.println("Creating edge labels...");
            for (JsonNode edgeLabel : root.get("edgeLabels")) {
                String name = edgeLabel.get("name").asText();
                mgmt.makeEdgeLabel(name).multiplicity(Multiplicity.MULTI).make();
            }

            // Create Property Keys
            System.out.println("Creating property keys...");
            for (JsonNode prop : root.get("propertyKeys")) {
                String name = prop.get("name").asText();
                String dataType = prop.get("dataType").asText();
                String cardinality = prop.get("cardinality").asText();

                // Map dataType string to Java class
                Class<?> clazz;
                switch (dataType) {
                    case "String": clazz = String.class; break;
                    case "Double": clazz = Double.class; break;
                    case "Integer": clazz = Integer.class; break;
                    default: clazz = String.class; // Fallback
                }

                mgmt.makePropertyKey(name)
                        .dataType(clazz)
                        .cardinality(Cardinality.valueOf(cardinality))
                        .make();
            }

            // Create Composite Indexes
            System.out.println("Creating composite indexes...");
            JsonNode compositeIndices = root.get("graphIndices").get("compositeIndices");
            for (JsonNode index : compositeIndices) {
                String indexName = index.get("indexName").asText();
                String elementType = index.get("elementType").asText();
                boolean unique = index.get("unique").asBoolean();

                JanusGraphManagement.IndexBuilder builder;
                if (elementType.equalsIgnoreCase("vertex")) {
                    builder = mgmt.buildIndex(indexName, Vertex.class);
                } else {
                    builder = mgmt.buildIndex(indexName, Edge.class);
                }

                for (JsonNode key : index.get("propertyKeys")) {
                    builder.addKey(mgmt.getPropertyKey(key.asText()));
                }

                if (index.has("indexOnly")) {
                    String labelName = index.get("indexOnly").asText();
                    builder.indexOnly(mgmt.getVertexLabel(labelName));
                }

                if (unique) {
                    builder.unique();
                }

                builder.buildCompositeIndex();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Commit the schema
        mgmt.commit();
        System.out.println("Schema initialization complete!");
    }

    private static void loadData(JanusGraph graph) {
        System.out.println("Loading data from CSV files...");

        Map<String, Vertex> airports = new HashMap<>();

        // Load airports
        try (Reader reader = new FileReader("clean_air-routes-latest-nodes.csv");
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            JanusGraphTransaction tx = graph.newTransaction();
            System.out.println("Inserting airport vertices...");

            int vertexCount = 0;
            for (CSVRecord record : parser) {
                Vertex airport = tx.addVertex(T.label, "airport");
                airport.property("code", record.get("code"));
                airport.property("icao", record.get("icao"));
                airport.property("desc", record.get("desc"));
                airport.property("city", record.get("city"));
                airport.property("country", record.get("country"));
                airport.property("lat", Double.parseDouble(record.get("lat")));
                airport.property("lon", Double.parseDouble(record.get("lon")));
                airport.property("type", record.get("type"));
                airport.property("region", record.get("region"));
                airport.property("runways", parseInt(record.get("runways")));
                airport.property("longest", parseInt(record.get("longest")));
                airport.property("elev", parseInt(record.get("elev")));
                airport.property("identity", record.get("id"));

                airports.put(record.get("code"), airport);
                vertexCount++;
            }
            tx.commit();
            System.out.println("Inserted " + vertexCount + " airport vertices.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Load routes
        try (Reader reader = new FileReader("clean_air-routes-latest-edges.csv");
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            JanusGraphTransaction tx = graph.newTransaction();
            System.out.println("Inserting route edges...");

            int edgeCount = 0;
            for (CSVRecord record : parser) {
                Vertex from = airports.get(record.get("src"));
                Vertex to = airports.get(record.get("dst"));
                if (from != null && to != null) {
                    Edge edge = from.addEdge("route", to);
                    edge.property("dist", record.get("dist"));
                    edge.property("identity", record.get("id"));
                    edgeCount++;
                }
            }
            tx.commit();
            System.out.println("Inserted " + edgeCount + " route edges.");
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Data loading complete!");
    }

    private static Integer parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
