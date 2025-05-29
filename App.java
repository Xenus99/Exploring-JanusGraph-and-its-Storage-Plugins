package com.sample.graph;

import org.apache.commons.csv.*;
import org.janusgraph.core.*;
import org.janusgraph.core.schema.JanusGraphManagement;
import org.apache.tinkerpop.gremlin.structure.*;

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

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(new File("air-routes-schema.json"));


        // Create Vertex Labels
        System.out.println("Creating vertex labels...");
        mgmt.makeVertexLabel("airport").make();
        mgmt.makeVertexLabel("country").make();
        mgmt.makeVertexLabel("continent").make();

        // Create Edge Labels
        System.out.println("Creating edge labels...");
        mgmt.makeEdgeLabel("route").multiplicity(Multiplicity.MULTI).make();
        mgmt.makeEdgeLabel("contains").multiplicity(Multiplicity.MULTI).make();

        // Create Property Keys
        System.out.println("Creating property keys...");
        mgmt.makePropertyKey("city").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("lat").dataType(Double.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("lon").dataType(Double.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("dist").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("identity").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("type").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("code").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("icao").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("desc").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("region").dataType(String.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("runways").dataType(Integer.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("longest").dataType(Integer.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("elev").dataType(Integer.class).cardinality(Cardinality.SINGLE).make();
        mgmt.makePropertyKey("country").dataType(String.class).cardinality(Cardinality.SINGLE).make();

        // Create Composite Indexes
        System.out.println("Creating composite indexes...");
        mgmt.buildIndex("Idx_comidx_Vertex_identity_unique", Vertex.class)
                .addKey(mgmt.getPropertyKey("identity")).unique().buildCompositeIndex();

        mgmt.buildIndex("Idx_comidx_Vertex_type_airport", Vertex.class)
                .addKey(mgmt.getPropertyKey("type")).indexOnly(mgmt.getVertexLabel("airport")).buildCompositeIndex();

        mgmt.buildIndex("Idx_comidx_Vertex_code", Vertex.class)
                .addKey(mgmt.getPropertyKey("code")).buildCompositeIndex();

        mgmt.buildIndex("Idx_comidx_Vertex_icao", Vertex.class)
                .addKey(mgmt.getPropertyKey("icao")).buildCompositeIndex();

        mgmt.buildIndex("Idx_comidx_Vertex_country", Vertex.class)
                .addKey(mgmt.getPropertyKey("country")).buildCompositeIndex();

        mgmt.buildIndex("Idx_comidx_Vertex_city", Vertex.class)
                .addKey(mgmt.getPropertyKey("city")).buildCompositeIndex();

        mgmt.buildIndex("Idx_comidx_Edge_identity", Edge.class)
                .addKey(mgmt.getPropertyKey("identity")).buildCompositeIndex();

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
