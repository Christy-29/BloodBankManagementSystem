
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BloodBankServer {

    private static final int PORT = 8080;
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/blood_bank_db";
    private static final String DB_USER = "root";
    private static final Path PUBLIC_DIR = Path.of("public");

    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");

        String password = System.getenv("BLOODBANK_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            System.err.println(
                    "Set BLOODBANK_DB_PASSWORD before starting the server."
            );
            return;
        }

        try (Connection con = getConnection()) {
            System.out.println("MySQL connection successful.");
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress(PORT), 0
        );

        server.createContext("/", BloodBankServer::handleRequest);
        server.setExecutor(null);
        server.start();

        System.out.println("Blood Bank website is running!");
        System.out.println("Open http://localhost:" + PORT);
    }

    private static Connection getConnection() throws SQLException {
        String password = System.getenv("BLOODBANK_DB_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new SQLException("Database password is not configured.");
        }

        return DriverManager.getConnection(DB_URL, DB_USER, password);
    }

    // ---------------- ROUTING ----------------

    private static void handleRequest(HttpExchange exchange)
            throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            if ("/".equals(path) && "GET".equals(method)) {
                serveFile(exchange, "index.html", "text/html; charset=UTF-8");
                return;
            }

            if ("/style.css".equals(path) && "GET".equals(method)) {
                serveFile(exchange, "style.css", "text/css; charset=UTF-8");
                return;
            }

            if ("/app.js".equals(path) && "GET".equals(method)) {
                serveFile(exchange, "app.js",
                        "application/javascript; charset=UTF-8");
                return;
            }

            if ("/api/dashboard".equals(path) && "GET".equals(method)) {
                getDashboard(exchange);
                return;
            }

            if ("/api/donors".equals(path)) {
                if ("GET".equals(method)) {
                    getDonors(exchange);
                } else if ("POST".equals(method)) {
                    addDonor(exchange);
                } else {
                    sendJson(exchange, 405, error("Method not allowed"));
                }
                return;
            }

            if ("/api/stock".equals(path) && "GET".equals(method)) {
                getStock(exchange);
                return;
            }

            if ("/api/donations".equals(path) && "POST".equals(method)) {
                addDonation(exchange);
                return;
            }

            if ("/api/requests".equals(path)) {
                if ("GET".equals(method)) {
                    getRequests(exchange);
                } else if ("POST".equals(method)) {
                    addRequest(exchange);
                } else {
                    sendJson(exchange, 405, error("Method not allowed"));
                }
                return;
            }

            if ("/api/requests/fulfil".equals(path)
                    && "POST".equals(method)) {
                fulfilRequest(exchange);
                return;
            }

            sendJson(exchange, 404, error("Endpoint not found"));

        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, error(e.getMessage()));
        } catch (SQLException e) {
            e.printStackTrace();
            sendJson(exchange, 500,
                    error("Database error. Check table and column names."));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(exchange, 500, error("Internal server error"));
        } finally {
            exchange.close();
        }
    }

    // ---------------- DASHBOARD ----------------

    private static void getDashboard(HttpExchange exchange)
            throws SQLException, IOException {

        String sql =
                "SELECT " +
                "(SELECT COUNT(*) FROM donors) AS total_donors, " +
                "(SELECT COALESCE(SUM(units_available), 0) FROM blood_stock) AS total_stock, " +
                "(SELECT COUNT(*) FROM blood_requests WHERE LOWER(status) = 'pending') AS pending_requests, " +
                "(SELECT COUNT(*) FROM blood_requests WHERE LOWER(status) = 'fulfilled') AS fulfilled_requests, " +
                "(SELECT COUNT(*) FROM blood_stock WHERE units_available <= 2) AS low_stock_groups";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            rs.next();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total_donors", rs.getInt("total_donors"));
            result.put("total_stock", rs.getInt("total_stock"));
            result.put("pending_requests", rs.getInt("pending_requests"));
            result.put("fulfilled_requests", rs.getInt("fulfilled_requests"));
            result.put("low_stock_groups", rs.getInt("low_stock_groups"));

            sendJson(exchange, 200, toJson(result));
        }
    }

    // ---------------- DONORS ----------------

    private static void getDonors(HttpExchange exchange)
            throws SQLException, IOException {

        String sql =
                "SELECT d.donor_id, d.name, d.age, d.gender, d.phone, " +
                "d.blood_group, d.city, " +
                "(SELECT COALESCE(SUM(n.units), 0) FROM donations n " +
                "WHERE n.donor_id = d.donor_id) AS units_donated " +
                "FROM donors d ORDER BY d.donor_id DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) {
                    json.append(",");
                }
                first = false;

                Map<String, Object> donor = new LinkedHashMap<>();
                donor.put("donor_id", rs.getInt("donor_id"));
                donor.put("name", rs.getString("name"));
                donor.put("age", rs.getInt("age"));
                donor.put("gender", rs.getString("gender"));
                donor.put("phone", rs.getString("phone"));
                donor.put("blood_group", rs.getString("blood_group"));
                donor.put("city", rs.getString("city"));
                donor.put("units_donated", rs.getInt("units_donated"));

                json.append(toJson(donor));
            }

            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    }

    private static void addDonor(HttpExchange exchange)
            throws SQLException, IOException {

        Map<String, String> data = readData(exchange);

        String name = required(data, "name");
        int age = positiveInt(required(data, "age"), "age");

        if (age > 120) {
            throw new IllegalArgumentException("Enter a valid age.");
        }

        String gender = required(data, "gender");
        String phone = required(data, "phone");
        String group = bloodGroup(required(data, "blood_group"));
        String city = required(data, "city");

        String sql =
                "INSERT INTO donors " +
                "(name, age, gender, phone, blood_group, city) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, name);
            ps.setInt(2, age);
            ps.setString(3, gender);
            ps.setString(4, phone);
            ps.setString(5, group);
            ps.setString(6, city);
            ps.executeUpdate();

            int id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Donor added successfully");
            result.put("donor_id", id);

            sendJson(exchange, 201, toJson(result));
        }
    }

    // ---------------- BLOOD STOCK ----------------

    private static void getStock(HttpExchange exchange)
            throws SQLException, IOException {

        String sql =
                "SELECT blood_group, units_available " +
                "FROM blood_stock ORDER BY blood_group";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) {
                    json.append(",");
                }
                first = false;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("blood_group", rs.getString("blood_group"));
                item.put("units_available", rs.getInt("units_available"));

                json.append(toJson(item));
            }

            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    }

    // ---------------- DONATIONS ----------------

    private static void addDonation(HttpExchange exchange)
            throws SQLException, IOException {

        Map<String, String> data = readData(exchange);

        int donorId = positiveInt(required(data, "donor_id"), "donor_id");
        int units = positiveInt(required(data, "units"), "units");

        if (units > 10) {
            throw new IllegalArgumentException("Maximum 10 units per entry.");
        }

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                String group;

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT blood_group FROM donors WHERE donor_id = ?")) {
                    ps.setInt(1, donorId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Donor not found.");
                        }
                        group = rs.getString("blood_group");
                    }
                }

                String insertDonation =
                        "INSERT INTO donations (donor_id, units, donation_date) " +
                        "VALUES (?, ?, ?)";

                try (PreparedStatement ps =
                             con.prepareStatement(insertDonation)) {
                    ps.setInt(1, donorId);
                    ps.setInt(2, units);
                    ps.setDate(3, Date.valueOf(LocalDate.now()));
                    ps.executeUpdate();
                }

                String updateStock =
                        "INSERT INTO blood_stock (blood_group, units_available) " +
                        "VALUES (?, ?) ON DUPLICATE KEY UPDATE " +
                        "units_available = units_available + VALUES(units_available)";

                try (PreparedStatement ps =
                             con.prepareStatement(updateStock)) {
                    ps.setString(1, group);
                    ps.setInt(2, units);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange, 201,
                        "{\"success\":true,\"message\":\"Donation recorded and stock updated\"}");

            } catch (Exception e) {
                con.rollback();

                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new SQLException(e);

            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // ---------------- REQUESTS ----------------

    private static void getRequests(HttpExchange exchange)
            throws SQLException, IOException {

        String sql =
                "SELECT request_id, patient_name, hospital_name, blood_group, " +
                "units_requested, status, created_at " +
                "FROM blood_requests ORDER BY request_id DESC";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;

            while (rs.next()) {
                if (!first) {
                    json.append(",");
                }
                first = false;

                Map<String, Object> request = new LinkedHashMap<>();
                request.put("request_id", rs.getInt("request_id"));
                request.put("patient_name", rs.getString("patient_name"));
                request.put("hospital_name", rs.getString("hospital_name"));
                request.put("blood_group", rs.getString("blood_group"));
                request.put("units_requested", rs.getInt("units_requested"));
                request.put("status", rs.getString("status"));

                Timestamp timestamp = rs.getTimestamp("created_at");
                request.put("created_at",
                        timestamp == null ? null : timestamp.toString());

                json.append(toJson(request));
            }

            json.append("]");
            sendJson(exchange, 200, json.toString());
        }
    }

    private static void addRequest(HttpExchange exchange)
            throws SQLException, IOException {

        Map<String, String> data = readData(exchange);

        String patient = required(data, "patient_name");
        String hospital = required(data, "hospital_name");
        String group = bloodGroup(required(data, "blood_group"));
        int units = positiveInt(
                required(data, "units_requested"), "units_requested");

        String sql =
                "INSERT INTO blood_requests " +
                "(patient_name, hospital_name, blood_group, units_requested, status, created_at) " +
                "VALUES (?, ?, ?, ?, 'Pending', CURRENT_TIMESTAMP)";

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, patient);
            ps.setString(2, hospital);
            ps.setString(3, group);
            ps.setInt(4, units);
            ps.executeUpdate();

            int id = 0;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getInt(1);
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", "Blood request added successfully");
            result.put("request_id", id);

            sendJson(exchange, 201, toJson(result));
        }
    }

    // ---------------- FULFIL REQUEST ----------------

    private static void fulfilRequest(HttpExchange exchange)
            throws SQLException, IOException {

        Map<String, String> data = readData(exchange);
        int requestId = positiveInt(
                required(data, "request_id"), "request_id");

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);

            try {
                String group;
                int units;
                String status;

                String requestSql =
                        "SELECT blood_group, units_requested, status " +
                        "FROM blood_requests WHERE request_id = ? FOR UPDATE";

                try (PreparedStatement ps = con.prepareStatement(requestSql)) {
                    ps.setInt(1, requestId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new IllegalArgumentException("Request not found.");
                        }
                        group = rs.getString("blood_group");
                        units = rs.getInt("units_requested");
                        status = rs.getString("status");
                    }
                }

                if ("fulfilled".equalsIgnoreCase(status)) {
                    throw new IllegalArgumentException(
                            "This request is already fulfilled.");
                }

                int available = 0;

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT units_available FROM blood_stock " +
                        "WHERE blood_group = ? FOR UPDATE")) {
                    ps.setString(1, group);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            available = rs.getInt("units_available");
                        }
                    }
                }

                if (available < units) {
                    throw new IllegalArgumentException(
                            "Insufficient stock. Available units: " + available);
                }

                String stockSql =
                        "UPDATE blood_stock " +
                        "SET units_available = units_available - ? " +
                        "WHERE blood_group = ? AND units_available >= ?";

                try (PreparedStatement ps = con.prepareStatement(stockSql)) {
                    ps.setInt(1, units);
                    ps.setString(2, group);
                    ps.setInt(3, units);

                    if (ps.executeUpdate() != 1) {
                        throw new IllegalArgumentException(
                                "Unable to update stock.");
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE blood_requests SET status = 'Fulfilled' " +
                        "WHERE request_id = ?")) {
                    ps.setInt(1, requestId);
                    ps.executeUpdate();
                }

                con.commit();

                sendJson(exchange, 200,
                        "{\"success\":true,\"message\":\"Request fulfilled and stock reduced\"}");

            } catch (Exception e) {
                con.rollback();

                if (e instanceof IllegalArgumentException) {
                    throw (IllegalArgumentException) e;
                }
                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }
                throw new SQLException(e);

            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    // ---------------- HELPERS ----------------

    private static Map<String, String> readData(HttpExchange exchange)
            throws IOException {

        byte[] bytes;
        try (InputStream input = exchange.getRequestBody()) {
            bytes = input.readAllBytes();
        }

        String body = new String(bytes, StandardCharsets.UTF_8).trim();

        if (body.isEmpty()) {
            throw new IllegalArgumentException("Request body is empty.");
        }

        Map<String, String> result = new LinkedHashMap<>();

        Pattern pattern = Pattern.compile(
                "\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*" +
                "(\"(?:\\\\.|[^\"\\\\])*\"|-?\\d+|true|false|null)",
                Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(body);

        while (matcher.find()) {
            String key = unescape(matcher.group(1));
            String raw = matcher.group(2);
            String value;

            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                value = unescape(raw.substring(1, raw.length() - 1));
            } else if ("null".equalsIgnoreCase(raw)) {
                value = "";
            } else {
                value = raw;
            }

            result.put(key, value);
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("Invalid JSON request.");
        }

        return result;
    }

    private static String required(Map<String, String> data, String key) {
        String value = data.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }

        return value.trim();
    }

    private static int positiveInt(String value, String field) {
        try {
            int number = Integer.parseInt(value);

            if (number <= 0) {
                throw new IllegalArgumentException(
                        field + " must be greater than zero.");
            }

            return number;

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    field + " must be a valid integer.");
        }
    }

    private static String bloodGroup(String value) {
        String group = value.trim().toUpperCase();

        if (!group.matches("A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-")) {
            throw new IllegalArgumentException("Invalid blood group.");
        }

        return group;
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            if (ch != '\\' || i + 1 >= value.length()) {
                result.append(ch);
                continue;
            }

            char next = value.charAt(++i);

            switch (next) {
                case '"': result.append('"'); break;
                case '\\': result.append('\\'); break;
                case '/': result.append('/'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (i + 4 < value.length()) {
                        try {
                            result.append((char) Integer.parseInt(
                                    value.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            result.append('u');
                        }
                    } else {
                        result.append('u');
                    }
                    break;
                default: result.append(next);
            }
        }

        return result.toString();
    }

    private static String toJson(Map<String, Object> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("\"")
                    .append(escapeJson(entry.getKey()))
                    .append("\":");

            Object value = entry.getValue();

            if (value == null) {
                json.append("null");
            } else if (value instanceof Number
                    || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"")
                        .append(escapeJson(value.toString()))
                        .append("\"");
            }
        }

        return json.append("}").toString();
    }

    private static String escapeJson(String value) {
        StringBuilder result = new StringBuilder();

        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"': result.append("\\\""); break;
                case '\\': result.append("\\\\"); break;
                case '\b': result.append("\\b"); break;
                case '\f': result.append("\\f"); break;
                case '\n': result.append("\\n"); break;
                case '\r': result.append("\\r"); break;
                case '\t': result.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        result.append(String.format("\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
            }
        }

        return result.toString();
    }

    private static String error(String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message);
        return toJson(result);
    }

    // ---------------- WEBSITE FILES ----------------

    private static void serveFile(
            HttpExchange exchange, String fileName, String contentType)
            throws IOException {

        Path file = PUBLIC_DIR.resolve(fileName).toAbsolutePath().normalize();

        if (!Files.isRegularFile(file)) {
            sendJson(exchange, 404,
                    error("Website file not found: " + fileName));
            return;
        }

        byte[] content = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, content.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }

    private static void sendJson(
            HttpExchange exchange, int status, String json)
            throws IOException {

        byte[] content = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(status, content.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(content);
        }
    }
}
