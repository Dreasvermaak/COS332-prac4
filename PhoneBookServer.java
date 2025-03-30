import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhoneBookServer {
    private static final int PORT = 8080;
    private static final String CONTACTS_FILE = "contacts.dat";
    private static List<Contact> contacts = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        // Load existing contacts
        loadContacts();

        // Create HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Set up context handlers for different paths
        server.createContext("/", new HomeHandler());
        server.createContext("/add", new AddContactHandler());
        server.createContext("/search", new SearchContactHandler());
        server.createContext("/delete", new DeleteContactHandler());
        server.createContext("/image", new ImageHandler());
        
        // Start the server
        server.setExecutor(null);
        server.start();
        
        System.out.println("Server started on port " + PORT);
        System.out.println("Open your browser and navigate to http://localhost:" + PORT);
    }

    // Load contacts from file
    private static void loadContacts() {
        try {
            File file = new File(CONTACTS_FILE);
            if (file.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    contacts = (List<Contact>) ois.readObject();
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading contacts: " + e.getMessage());
            contacts = new ArrayList<>();
        }
    }

    // Save contacts to file
    private static void saveContacts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CONTACTS_FILE))) {
            oos.writeObject(contacts);
        } catch (Exception e) {
            System.err.println("Error saving contacts: " + e.getMessage());
        }
    }

    // Parse query parameters from URL
    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    // Parse form data from POST request
    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        return parseQueryParams(requestBody.toString());
    }

    // Home page handler
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Get query parameters if any
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String message = params.getOrDefault("message", "");

            // Create HTML response
            StringBuilder response = new StringBuilder();
            response.append("<!DOCTYPE html><html><head><title>Phone Book</title>");
            response.append("<style>");
            response.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f8f9fa; }");
            response.append("h1, h2 { color: #333; }");
            response.append(".container { display: flex; gap: 20px; margin-bottom: 20px; }");
            response.append(".section { flex: 1; background: #ffffff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
            response.append("input, button { margin: 8px 0; padding: 10px; width: 100%; box-sizing: border-box; border: 1px solid #ddd; border-radius: 4px; }");
            response.append("button { background-color: #4CAF50; color: white; border: none; cursor: pointer; font-weight: bold; }");
            response.append("button:hover { background-color: #45a049; }");
            response.append("table { width: 100%; border-collapse: collapse; margin-top: 20px; background: white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); border-radius: 8px; overflow: hidden; }");
            response.append("table, th, td { border: 1px solid #ddd; padding: 12px; }");
            response.append("th { background-color: #f2f2f2; text-align: left; }");
            response.append("tr:nth-child(even) { background-color: #f9f9f9; }");
            response.append("tr:hover { background-color: #f1f1f1; }");
            response.append(".message { color: #4CAF50; font-weight: bold; padding: 10px; background-color: #e8f5e9; border-radius: 4px; margin-bottom: 20px; }");
            response.append(".contact-image { max-width: 100px; max-height: 100px; border-radius: 4px; object-fit: contain; }");
            response.append(".small { font-size: 12px; color: #777; margin-top: 4px; }");
            response.append("</style>");
            response.append("</head><body>");
            
            // Add message if there is one
            if (!message.isEmpty()) {
                response.append("<div class=\"message\">").append(message).append("</div>");
            }
            
            // Title
            response.append("<h1>Phone Book Application</h1>");
            
            response.append("<div class=\"container\">");
            
            // Add contact form
            response.append("<div class=\"section\">");
            response.append("<h2>Add Contact</h2>");
            response.append("<form action=\"/add\" method=\"post\" enctype=\"multipart/form-data\">");
            response.append("Name: <input type=\"text\" name=\"name\" required><br>");
            response.append("Phone: <input type=\"text\" name=\"phone\" placeholder=\"Home/Office Phone\" required><br>");
            response.append("Cell Phone: <input type=\"text\" name=\"cellPhone\" placeholder=\"Mobile Number\" required><br>");
            response.append("Photo: <input type=\"file\" name=\"photo\" accept=\"image/*\"><br>");
            response.append("<p class=\"small\">Supported formats: JPEG, PNG, GIF</p>");
            response.append("<button type=\"submit\">Add Contact</button>");
            response.append("</form>");
            response.append("</div>");
            
            // Search form
            response.append("<div class=\"section\">");
            response.append("<h2>Search Contact</h2>");
            response.append("<form action=\"/search\" method=\"get\">");
            response.append("Name: <input type=\"text\" name=\"query\" required><br>");
            response.append("<button type=\"submit\">Search</button>");
            response.append("</form>");
            response.append("</div>");
            
            response.append("</div>");
            
            // Display contacts
            response.append("<h2>Contacts</h2>");
            response.append("<table>");
            response.append("<tr><th>Name</th><th>Phone</th><th>Cell Phone</th><th>Photo</th><th>Action</th></tr>");
            
            for (int i = 0; i < contacts.size(); i++) {
                Contact contact = contacts.get(i);
                response.append("<tr>");
                response.append("<td>").append(contact.getName()).append("</td>");
                response.append("<td>").append(contact.getPhone()).append("</td>");
                response.append("<td>").append(contact.getCellPhone()).append("</td>");
                
                // Display image if available
                response.append("<td>");
                if (contact.hasPhoto()) {
                    // Add a timestamp parameter to prevent caching
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    response.append("<div style=\"width: 100px; height: 100px; display: flex; align-items: center; justify-content: center; background-color: #f5f5f5;\">");
                    response.append("<img src=\"/image?id=").append(i).append("&t=").append(timestamp).append("\" class=\"contact-image\" alt=\"Photo of ").append(contact.getName()).append("\">");
                    response.append("</div>");
                } else {
                    response.append("No image");
                }
                response.append("</td>");
                
                // Delete button
                response.append("<td><form action=\"/delete\" method=\"post\">");
                response.append("<input type=\"hidden\" name=\"id\" value=\"").append(i).append("\">");
                response.append("<button type=\"submit\">Delete</button>");
                response.append("</form></td>");
                
                response.append("</tr>");
            }
            
            response.append("</table>");
            response.append("</body></html>");

            // Send response
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // Add contact handler
    static class AddContactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                
                try {
                    // Check if it's a simple form or multipart form
                    if (contentType != null && contentType.startsWith("multipart/form-data")) {
                        // Process multipart form data using a more reliable approach
                        String boundary = extractBoundary(contentType);
                        
                        if (boundary != null) {
                            // Read all data from input stream
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            InputStream is = exchange.getRequestBody();
                            while ((bytesRead = is.read(buffer)) != -1) {
                                baos.write(buffer, 0, bytesRead);
                            }
                            byte[] requestData = baos.toByteArray();
                            
                            // Parse the multipart form data
                            Map<String, Object> formData = parseMultipartForm(requestData, boundary);
                            
                            // Get the form fields
                            String name = (String) formData.getOrDefault("name", "");
                            String phone = (String) formData.getOrDefault("phone", "");
                            String cellPhone = (String) formData.getOrDefault("cellPhone", "");
                            byte[] photoData = (byte[]) formData.getOrDefault("photo", null);
                            
                            if (!name.isEmpty() && !phone.isEmpty() && !cellPhone.isEmpty()) {
                                Contact contact = new Contact(name, phone, cellPhone);
                                
                                // Set photo if available
                                if (photoData != null && photoData.length > 0) {
                                    System.out.println("Adding photo for contact: " + name + " (size: " + photoData.length + " bytes)");
                                    contact.setPhoto(photoData);
                                    
                                    // Debug output to verify image data
                                    byte[] savedPhoto = contact.getPhoto();
                                    if (savedPhoto != null) {
                                        System.out.println("Verified photo storage: " + savedPhoto.length + " bytes");
                                    } else {
                                        System.err.println("ERROR: Failed to save photo data!");
                                    }
                                }
                                
                                contacts.add(contact);
                                saveContacts();
                                
                                // Redirect to home page with success message
                                exchange.getResponseHeaders().set("Location", "/?message=Contact+added+successfully");
                                exchange.sendResponseHeaders(302, -1);
                                return;
                            }
                        }
                    } else {
                        // Simple form data
                        Map<String, String> formData = parseFormData(exchange.getRequestBody());
                        
                        String name = formData.getOrDefault("name", "");
                        String phone = formData.getOrDefault("phone", "");
                        String cellPhone = formData.getOrDefault("cellPhone", "");
                        
                        if (!name.isEmpty() && !phone.isEmpty() && !cellPhone.isEmpty()) {
                            contacts.add(new Contact(name, phone, cellPhone));
                            saveContacts();
                            
                            // Redirect to home page with success message
                            exchange.getResponseHeaders().set("Location", "/?message=Contact+added+successfully");
                            exchange.sendResponseHeaders(302, -1);
                            return;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error adding contact: " + e.getMessage());
                    e.printStackTrace();
                }
                
                // Error - redirect back to home
                exchange.getResponseHeaders().set("Location", "/?message=Error+adding+contact");
                exchange.sendResponseHeaders(302, -1);
            } else {
                // Method not allowed
                exchange.sendResponseHeaders(405, -1);
            }
        }
        
        // Extract boundary from content type
        private String extractBoundary(String contentType) {
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
                int boundaryIndex = contentType.indexOf("boundary=");
                if (boundaryIndex != -1) {
                    return contentType.substring(boundaryIndex + 9);
                }
            }
            return null;
        }
        
        // Parse multipart form data - improved version that handles binary data
        private Map<String, Object> parseMultipartForm(byte[] data, String boundary) throws IOException {
            Map<String, Object> formFields = new HashMap<>();
            
            try {
                // Add two dashes before boundary as per spec
                String fullBoundary = "--" + boundary;
                
                // Convert data to string for header processing
                String dataStr = new String(data, StandardCharsets.ISO_8859_1);
                
                // Find boundary positions
                int pos = 0;
                int boundaryLength = fullBoundary.length();
                List<Integer> positions = new ArrayList<>();
                
                while ((pos = dataStr.indexOf(fullBoundary, pos)) != -1) {
                    positions.add(pos);
                    pos += boundaryLength;
                }
                
                // Process each part
                for (int i = 0; i < positions.size() - 1; i++) {
                    int start = positions.get(i) + boundaryLength;
                    int end = positions.get(i + 1);
                    
                    // Skip CRLF after boundary
                    if (start < dataStr.length() && dataStr.charAt(start) == '\r' && start + 1 < dataStr.length() && dataStr.charAt(start + 1) == '\n') {
                        start += 2;
                    }
                    
                    // Find headers end (double CRLF)
                    int headersEnd = dataStr.indexOf("\r\n\r\n", start);
                    if (headersEnd == -1 || headersEnd >= end) continue;
                    
                    String headers = dataStr.substring(start, headersEnd);
                    int contentStart = headersEnd + 4; // Skip \r\n\r\n
                    
                    // Check for CRLF before next boundary
                    int contentEnd = end;
                    if (contentEnd - 2 >= contentStart && dataStr.charAt(contentEnd - 2) == '\r' && dataStr.charAt(contentEnd - 1) == '\n') {
                        contentEnd -= 2;
                    }
                    
                    // Extract field name and check if it's a file
                    String fieldName = extractFieldName(headers);
                    boolean isFile = headers.contains("filename=\"") && !headers.contains("filename=\"\"");
                    
                    if (fieldName == null) continue;
                    
                    if (isFile) {
                        // For file uploads, store the binary data
                        byte[] fileData = new byte[contentEnd - contentStart];
                        System.arraycopy(data, contentStart, fileData, 0, fileData.length);
                        formFields.put(fieldName, fileData);
                        System.out.println("Found file for field: " + fieldName + ", size: " + fileData.length + " bytes");
                    } else {
                        // For regular fields, convert to string
                        String value = dataStr.substring(contentStart, contentEnd);
                        formFields.put(fieldName, value);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing multipart form: " + e.getMessage());
                e.printStackTrace();
            }
            
            return formFields;
        }
        
        // Find the end of headers (double CRLF)
        private int findHeaderEnd(byte[] data, int start, int end) {
            for (int i = start; i < end - 3; i++) {
                if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                    return i + 4;
                }
            }
            return -1;
        }
        
        // Extract field name from headers
        private String extractFieldName(String headers) {
            int nameStart = headers.indexOf("name=\"");
            if (nameStart != -1) {
                nameStart += 6; // Skip 'name="'
                int nameEnd = headers.indexOf("\"", nameStart);
                if (nameEnd != -1) {
                    return headers.substring(nameStart, nameEnd);
                }
            }
            return null;
        }
    }

    // Search contact handler
    static class SearchContactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Get query parameters
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String searchQuery = params.getOrDefault("query", "").toLowerCase();
            
            // Create HTML response
            StringBuilder response = new StringBuilder();
            response.append("<!DOCTYPE html><html><head><title>Search Results</title>");
            response.append("<style>");
            response.append("body { font-family: Arial, sans-serif; margin: 20px; }");
            response.append("h1, h2 { color: #333; }");
            response.append("table { width: 100%; border-collapse: collapse; }");
            response.append("table, th, td { border: 1px solid #ddd; padding: 8px; }");
            response.append("th { background-color: #f2f2f2; text-align: left; }");
            response.append("tr:nth-child(even) { background-color: #f9f9f9; }");
            response.append(".contact-image { max-width: 100px; max-height: 100px; }");
            response.append("</style>");
            response.append("</head><body>");
            
            response.append("<h1>Search Results for \"").append(searchQuery).append("\"</h1>");
            response.append("<a href=\"/\">Back to Home</a>");
            
            // Display matching contacts
            response.append("<table>");
            response.append("<tr><th>Name</th><th>Phone</th><th>Cell Phone</th><th>Photo</th></tr>");
            
            boolean foundResults = false;
            for (int i = 0; i < contacts.size(); i++) {
                Contact contact = contacts.get(i);
                if (contact.getName().toLowerCase().contains(searchQuery) || 
                    contact.getPhone().toLowerCase().contains(searchQuery) || 
                    contact.getCellPhone().toLowerCase().contains(searchQuery)) {
                    foundResults = true;
                    response.append("<tr>");
                    response.append("<td>").append(contact.getName()).append("</td>");
                    response.append("<td>").append(contact.getPhone()).append("</td>");
                    response.append("<td>").append(contact.getCellPhone()).append("</td>");
                    
                    // Display image if available
                    response.append("<td>");
                    if (contact.hasPhoto()) {
                        // Add a timestamp parameter to prevent caching
                        String timestamp = String.valueOf(System.currentTimeMillis());
                        response.append("<div style=\"width: 100px; height: 100px; display: flex; align-items: center; justify-content: center; background-color: #f5f5f5;\">");
                        response.append("<img src=\"/image?id=").append(i).append("&t=").append(timestamp).append("\" class=\"contact-image\" alt=\"Photo of ").append(contact.getName()).append("\">");
                        response.append("</div>");
                    } else {
                        response.append("No image");
                    }
                    response.append("</td>");
                    
                    response.append("</tr>");
                }
            }
            
            response.append("</table>");
            
            if (!foundResults) {
                response.append("<p>No contacts found matching \"").append(searchQuery).append("\"</p>");
            }
            
            response.append("</body></html>");

            // Send response
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // Delete contact handler
    static class DeleteContactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                Map<String, String> formData = parseFormData(exchange.getRequestBody());
                String idStr = formData.getOrDefault("id", "");
                
                try {
                    int id = Integer.parseInt(idStr);
                    if (id >= 0 && id < contacts.size()) {
                        contacts.remove(id);
                        saveContacts();
                        
                        // Redirect to home page with success message
                        exchange.getResponseHeaders().set("Location", "/?message=Contact+deleted+successfully");
                        exchange.sendResponseHeaders(302, -1);
                        return;
                    }
                } catch (NumberFormatException e) {
                    // Invalid ID format
                }
                
                // Error - redirect back to home
                exchange.getResponseHeaders().set("Location", "/?message=Error+deleting+contact");
                exchange.sendResponseHeaders(302, -1);
            } else {
                // Method not allowed
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // Helper method to detect image type from binary data
    private static String detectImageType(byte[] data) {
        if (data == null || data.length < 2) {
            System.out.println("Image data is too short or null");
            return "image/jpeg"; // Default
        }
        
        // Print the first few bytes for debugging
        System.out.println("Image signature bytes: " + 
                           String.format("%02X %02X %02X %02X", 
                                         data.length > 0 ? data[0] & 0xFF : 0, 
                                         data.length > 1 ? data[1] & 0xFF : 0,
                                         data.length > 2 ? data[2] & 0xFF : 0,
                                         data.length > 3 ? data[3] & 0xFF : 0));
        
        // Check for JPEG header (FF D8)
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            System.out.println("Detected JPEG image");
            return "image/jpeg";
        }
        
        // Check for PNG header (89 50 4E 47)
        if (data.length >= 4 && 
            (data[0] & 0xFF) == 0x89 && 
            (data[1] & 0xFF) == 0x50 && 
            (data[2] & 0xFF) == 0x4E && 
            (data[3] & 0xFF) == 0x47) {
            System.out.println("Detected PNG image");
            return "image/png";
        }
        
        // Check for GIF header (47 49 46)
        if (data.length >= 3 && 
            (data[0] & 0xFF) == 0x47 && 
            (data[1] & 0xFF) == 0x49 && 
            (data[2] & 0xFF) == 0x46) {
            System.out.println("Detected GIF image");
            return "image/gif";
        }
        
        // Default to JPEG if unknown
        System.out.println("Unknown image format, defaulting to JPEG");
        return "image/jpeg";
    }
    
    // Serve images
    static class ImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Get the image ID from query parameters
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String idStr = params.getOrDefault("id", "");
            
            try {
                int id = Integer.parseInt(idStr);
                if (id >= 0 && id < contacts.size() && contacts.get(id).hasPhoto()) {
                    Contact contact = contacts.get(id);
                    byte[] imageData = contact.getPhoto();
                    
                    // Debug output to verify we're getting the right contact and image
                    System.out.println("Serving image for contact: " + contact.getName() + 
                                       " (ID: " + id + ") with image size: " + 
                                       (imageData != null ? imageData.length : 0) + " bytes");
                    
                    if (imageData == null || imageData.length == 0) {
                        // No image data available
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    
                    // Attempt to detect image type from the data
                    String contentType = detectImageType(imageData);
                    
                    // Add a unique query parameter to prevent browser caching
                    String cacheBreaker = String.valueOf(System.currentTimeMillis());
                    
                    // Set appropriate headers
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                    exchange.getResponseHeaders().set("Pragma", "no-cache");
                    exchange.getResponseHeaders().set("Expires", "0");
                    exchange.sendResponseHeaders(200, imageData.length);
                    
                    // Write the image data to the response
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(imageData);
                        os.flush();
                    }
                    return;
                } else {
                    System.out.println("Image not found or contact has no photo. ID: " + id + 
                                      ", valid ID range: 0-" + (contacts.size()-1) + 
                                      ", has photo: " + (id < contacts.size() ? contacts.get(id).hasPhoto() : "N/A"));
                }
            } catch (NumberFormatException e) {
                // Invalid ID format
                System.err.println("Invalid image ID: " + idStr);
            } catch (Exception e) {
                // Other errors
                System.err.println("Error serving image: " + e.getMessage());
                e.printStackTrace();
            }
            
            // Image not found or invalid ID
            exchange.sendResponseHeaders(404, -1);
        }
    }
}