import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class AuthServer {
  
    public static final Map<String, String> USERS = new HashMap<>();
    
    public static final Set<String> validSessions = new HashSet<>();

    static {
        USERS.put("user@example.com", "password123");
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        
        server.createContext("/", new AuthHandler());
        server.createContext("/login", new LoginHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }
}

class AuthHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");

        if (cookieHeader == null || !isValidSession(cookieHeader)) {
          
            exchange.getResponseHeaders().set("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            return;
        }

  
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        serveStaticFile(exchange, path);
    }

    private boolean isValidSession(String cookieHeader) {
        
        return AuthServer.validSessions.stream().anyMatch(cookieHeader::contains);
    }

    private void serveStaticFile(HttpExchange exchange, String filename) throws IOException {
        File file = new File("public" + filename);
        if (!file.exists()) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }

        byte[] content = Files.readAllBytes(file.toPath());
        String contentType = filename.endsWith(".css") ? "text/css" : "text/html";

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(content);
        }
    }
}

class LoginHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equals(exchange.getRequestMethod())) {
            String body = new String(exchange.getRequestBody().readAllBytes());
            Map<String, String> params = parseFormData(body);

            String email = params.get("email");
            String pass = params.get("password");

            
            if (AuthServer.USERS.containsKey(email) && AuthServer.USERS.get(email).equals(pass)) {
                String sessionId = UUID.randomUUID().toString();
                AuthServer.validSessions.add(sessionId);

                
                exchange.getResponseHeaders().add("Set-Cookie", "session=" + sessionId + "; Path=/; HttpOnly");
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(302, -1);
                return;
            }
        }

        
        String html = """
            <html>
            <body style="font-family:sans-serif; text-align:center; margin-top:50px;">
                <h2>Login Required</h2>
                <form method="POST" action="/login">
                    <input type="email" name="email" placeholder="Email" required><br><br>
                    <input type="password" name="password" placeholder="Password" required><br><br>
                    <button type="submit">Login</button>
                </form>
            </body>
            </html>
            """;
        exchange.getResponseHeaders().set("Content-Type", "text/html");
        byte[] response = html.getBytes();
        exchange.sendResponseHeaders(200, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private Map<String, String> parseFormData(String formData) {
        Map<String, String> result = new HashMap<>();
        for (String pair : formData.split("&")) {
            String[] kv = pair.split("=");
            if (kv.length > 1) {
                result.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                           URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}
