
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.*;
import java.util.concurrent.Executors;

public class BloodBankServer {

    private static final String DB_URL =
        "jdbc:mysql://localhost:3306/blood_bank_db";

    private static final String DB_USER = "root";

    // Replace with the password used in DbTest.java
    private static final String DB_PASSWORD =
        "christy@29";

    public static void main(String[] args) throws Exception {

        Class.forName("com.mysql.cj.jdbc.Driver");

        HttpServer server = HttpServer.create(
            new InetSocketAddress("localhost", 8080), 0
        );

        server.createContext("/", BloodBankServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("Blood Bank website is running!");
        System.out.println("Open http://localhost:8080");
    }

    private static void handle(HttpExchange ex)
            throws IOException {

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();

        try {

            // Website files
            if (method.equals("GET") && path.equals("/")) {
                sendFile(ex, "public/index.html",
                    "text/html; charset=UTF-8");
                return;
            }

            if (method.equals("GET") &&
                path.equals("/style.css")) {
                sendFile(ex, "public/style.css",
                    "text/css; charset=UTF-8");
                return;
            }

            if (method.equals("GET") &&
                path.equals("/app.js")) {
                sendFile(ex, "public/app.js",
                    "application/javascript; charset=UTF-8");
                return;
            }

            // Donor API
            if (method.equals("GET") &&
                path.equals("/api/donors")) {
                getDonors(ex);
                return;
            }

            if (method.equals("POST") &&
                path.equals("/api/donors")) {
                addDonor(ex);
                return;
            }

            // Blood stock API
            if (method.equals("GET") &&
                path.equals("/api/stock")) {
                getStock(ex);
                return;
            }

            // Donation API
            if (method.equals("POST") &&
                path.equals("/api/donations")) {
                addDonation(ex);
                return;
            }

            sendJson(ex, 404,
                "{\"error\":\"Endpoint not found\"}");

        } catch (IllegalArgumentException e) {
            sendJson(ex, 400,
                "{\"error\":" + quote(e.getMessage()) + "}");

        } catch (Exception e) {
            e.printStackTrace();

            sendJson(ex, 500,
                "{\"error\":\"Server or database error. Check Command Prompt.\"}");
        }
    }

    // Serve HTML, CSS and JavaScript
    private static void sendFile(
        HttpExchange ex, String filename, String type
    ) throws IOException {

        java.nio.file.Path file = Paths.get(filename);

        if (!Files.isRegularFile(file)) {
            sendJson(ex, 404,
                "{\"error\":\"Website file not found\"}");
            return;
        }

        byte[] data = Files.readAllBytes(file);

        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set(
            "X-Content-Type-Options", "nosniff");

        ex.sendResponseHeaders(200, data.length);

        try (var out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    // Register donor
    private static void addDonor(HttpExchange ex)
            throws Exception {

        Map<String, String> f = readForm(ex);

        String name = required(f, "name");
        String gender = required(f, "gender");
        String phone = required(f, "phone");
        String group = required(f, "blood_group");
        String city = required(f, "city");

        int age;

        try {
            age = Integer.parseInt(required(f, "age"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Age must be a whole number.");
        }

        if (age < 18 || age > 100) {
            throw new IllegalArgumentException(
                "Age must be between 18 and 100.");
        }

        if (name.length() > 100 ||
            gender.length() > 20 ||
            phone.length() > 15 ||
            city.length() > 100) {
            throw new IllegalArgumentException(
                "One or more fields are too long.");
        }

        if (!Arrays.asList("Female", "Male", "Other")
                .contains(gender)) {
            throw new IllegalArgumentException(
                "Invalid gender.");
        }

        checkGroup(group);

        String sql =
            "INSERT INTO donors " +
            "(name, age, gender, phone, blood_group, city) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = connect();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setInt(2, age);
            ps.setString(3, gender);
            ps.setString(4, phone);
            ps.setString(5, group);
            ps.setString(6, city);

            ps.executeUpdate();
        }

        sendJson(ex, 201,
            "{\"message\":\"Donor registered successfully\"}");
    }

    // Record donation and update blood stock
    private static void addDonation(HttpExchange ex)
            throws Exception {

        Map<String, String> f = readForm(ex);

        int donorId;
        int units;

        try {
            donorId = Integer.parseInt(
                required(f, "donor_id"));
            units = Integer.parseInt(
                required(f, "units"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Donor ID and units must be whole numbers.");
        }

        if (donorId <= 0 || units <= 0 || units > 5) {
            throw new IllegalArgumentException(
                "Enter a valid donor ID and 1 to 5 demo units.");
        }

        try (Connection con = connect()) {

            con.setAutoCommit(false);

            try {
                String group;

                // Find the donor's blood group
                try (PreparedStatement ps = con.prepareStatement(
                    "SELECT blood_group FROM donors WHERE donor_id=?")) {

                    ps.setInt(1, donorId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException(
                                "Donor ID not found.");
                        }

                        group = rs.getString("blood_group");
                    }
                }

                // Insert donation record
                try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO donations (donor_id, units) " +
                    "VALUES (?, ?)")) {

                    ps.setInt(1, donorId);
                    ps.setInt(2, units);
                    ps.executeUpdate();
                }

                // Increase blood stock
                try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE blood_stock " +
                    "SET units_available = units_available + ? " +
                    "WHERE blood_group = ?")) {

                    ps.setInt(1, units);
                    ps.setString(2, group);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException(
                            "Blood stock record not found.");
                    }
                }

                // Save both changes together
                con.commit();

            } catch (Exception e) {
                con.rollback();
                throw e;

            } finally {
                con.setAutoCommit(true);
            }
        }

        sendJson(ex, 201,
            "{\"message\":\"Donation recorded and stock updated\"}");
    }

    // Retrieve donors
    private static void getDonors(HttpExchange ex)
            throws Exception {

        String sql =
            "SELECT donor_id, name, age, gender, phone, " +
            "blood_group, city FROM donors " +
            "ORDER BY donor_id DESC";

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            sendJson(ex, 200, resultSetToJson(rs));
        }
    }

    // Retrieve blood stock
    private static void getStock(HttpExchange ex)
            throws Exception {

        String sql =
            "SELECT blood_group, units_available " +
            "FROM blood_stock ORDER BY blood_group";

        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {

            sendJson(ex, 200, resultSetToJson(rs));
        }
    }

    private static Connection connect()
            throws SQLException {

        return DriverManager.getConnection(
            DB_URL, DB_USER, DB_PASSWORD);
    }

    private static Map<String, String> readForm(
        HttpExchange ex
    ) throws IOException {

        String body = new String(
            ex.getRequestBody().readAllBytes(),
            StandardCharsets.UTF_8);

        Map<String, String> data = new HashMap<>();

        for (String item : body.split("&")) {
            if (item.isEmpty()) continue;

            String[] pair = item.split("=", 2);

            String key = URLDecoder.decode(
                pair[0], StandardCharsets.UTF_8);

            String value = pair.length > 1
                ? URLDecoder.decode(
                    pair[1], StandardCharsets.UTF_8)
                : "";

            data.put(key, value);
        }

        return data;
    }

    private static String required(
        Map<String, String> f, String key
    ) {

        String value = f.get(key);

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Please enter " + key.replace('_', ' ') + ".");
        }

        return value.trim();
    }

    private static void checkGroup(String group) {
        if (!Arrays.asList(
            "A+", "A-", "B+", "B-",
            "AB+", "AB-", "O+", "O-"
        ).contains(group)) {
            throw new IllegalArgumentException(
                "Invalid blood group.");
        }
    }

    // Convert SQL results into JSON
    private static String resultSetToJson(ResultSet rs)
            throws SQLException {

        StringBuilder json = new StringBuilder("[");
        ResultSetMetaData md = rs.getMetaData();
        int columns = md.getColumnCount();
        boolean firstRow = true;

        while (rs.next()) {
            if (!firstRow) json.append(",");
            firstRow = false;

            json.append("{");

            for (int i = 1; i <= columns; i++) {
                if (i > 1) json.append(",");

                String key = md.getColumnLabel(i);
                Object value = rs.getObject(i);

                json.append(quote(key)).append(":");

                if (value == null) {
                    json.append("null");
                } else if (value instanceof Number) {
                    json.append(value);
                } else {
                    json.append(quote(value.toString()));
                }
            }

            json.append("}");
        }

        return json.append("]").toString();
    }

    private static String quote(String value) {
        if (value == null) return "null";

        return "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("\b", "\\b")
            .replace("\f", "\\f") + "\"";
    }

    private static void sendJson(
        HttpExchange ex, int status, String body
    ) throws IOException {

        byte[] data = body.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8");

        ex.getResponseHeaders().set(
            "X-Content-Type-Options", "nosniff");

        ex.sendResponseHeaders(status, data.length);

        try (var out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
