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
    // setting up basic stuff
    private static final int PORT = 8080; // the port our server runs on
    private static final String CONTACTS_FILE = "contacts.dat"; // where we save our contacts
    private static List<Contact> contacts = new ArrayList<>(); // our in-memory list of contacts

    public static void main(String[] args) throws IOException {
        // get existing contacts first
        loadContacts();

        // setup the HTTP server - this is from Java docs
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // map URLs to their handlers
        server.createContext("/", new HomeHandler()); // homepage
        server.createContext("/add", new AddContactHandler()); // adding contacts
        server.createContext("/search", new SearchContactHandler()); // searching contacts
        server.createContext("/delete", new DeleteContactHandler()); // deleting contacts
        server.createContext("/image", new ImageHandler()); // displaying contact images
        
        // fire up the server
        server.setExecutor(null); // just use default executor
        server.start();
        
        System.out.println("Server started on port " + PORT);
        System.out.println("Open your browser and navigate to http://localhost:" + PORT);
    }

    // load contacts from our saved file
    private static void loadContacts() {
        try {
            File file = new File(CONTACTS_FILE);
            if (file.exists()) { // only try to load if file exists
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                    contacts = (List<Contact>) ois.readObject(); // read the whole list at once
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading contacts: " + e.getMessage());
            contacts = new ArrayList<>(); // just start with empty list if something goes wrong
        }
    }

    // save contacts to file
    private static void saveContacts() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(CONTACTS_FILE))) {
            oos.writeObject(contacts); // write the whole list at once
        } catch (Exception e) {
            System.err.println("Error saving contacts: " + e.getMessage());
        }
    }

    // process URL parameters into a map
    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&"); // parameters are separated by &
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

    // get form data from POST requests
    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder requestBody = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            requestBody.append(line);
        }
        return parseQueryParams(requestBody.toString()); // reuse our query parser
    }

    // handle the home page
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // grab any URL parameters
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String message = params.getOrDefault("message", "");

            // build the HTML for our page
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
            
            // show success/error message if we have one
            if (!message.isEmpty()) {
                response.append("<div class=\"message\">").append(message).append("</div>");
            }
            
            // page title
            response.append("<h1>Phone Book Application</h1>");
            
            response.append("<div class=\"container\">");
            
            // form for adding contacts
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
            
            // form for searching contacts
            response.append("<div class=\"section\">");
            response.append("<h2>Search Contact</h2>");
            response.append("<form action=\"/search\" method=\"get\">");
            response.append("Name: <input type=\"text\" name=\"query\" required><br>");
            response.append("<button type=\"submit\">Search</button>");
            response.append("</form>");
            response.append("</div>");
            
            response.append("</div>");
            
            // show all contacts in a table
            response.append("<h2>Contacts</h2>");
            response.append("<table>");
            response.append("<tr><th>Name</th><th>Phone</th><th>Cell Phone</th><th>Photo</th><th>Action</th></tr>");
            
            for (int i = 0; i < contacts.size(); i++) {
                Contact contact = contacts.get(i);
                response.append("<tr>");
                response.append("<td>").append(contact.getName()).append("</td>");
                response.append("<td>").append(contact.getPhone()).append("</td>");
                response.append("<td>").append(contact.getCellPhone()).append("</td>");
                
                // show image if available
                response.append("<td>");
                if (contact.hasPhoto()) {
                    // add timestamp to prevent browser caching
                    String timestamp = String.valueOf(System.currentTimeMillis());
                    response.append("<div style=\"width: 100px; height: 100px; display: flex; align-items: center; justify-content: center; background-color: #f5f5f5;\">");
                    response.append("<img src=\"/image?id=").append(i).append("&t=").append(timestamp).append("\" class=\"contact-image\" alt=\"Photo of ").append(contact.getName()).append("\">");
                    response.append("</div>");
                } else {
                    response.append("No image");
                }
                response.append("</td>");
                
                // delete button for each contact
                response.append("<td><form action=\"/delete\" method=\"post\">");
                response.append("<input type=\"hidden\" name=\"id\" value=\"").append(i).append("\">");
                response.append("<button type=\"submit\">Delete</button>");
                response.append("</form></td>");
                
                response.append("</tr>");
            }
            
            response.append("</table>");
            response.append("</body></html>");

            // send back our HTML page
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // handles adding new contacts
    static class AddContactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                
                try {
                    // check if it's a regular form or one with file uploads
                    if (contentType != null && contentType.startsWith("multipart/form-data")) {
                        // handle multipart form data - needed for file uploads
                        String boundary = extractBoundary(contentType);
                        
                        if (boundary != null) {
                            // read all the request data
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            InputStream is = exchange.getRequestBody();
                            while ((bytesRead = is.read(buffer)) != -1) {
                                baos.write(buffer, 0, bytesRead);
                            }
                            byte[] requestData = baos.toByteArray();
                            
                            // parse all the form fields and files
                            Map<String, Object> formData = parseMultipartForm(requestData, boundary);
                            
                            // grab our form values
                            String name = (String) formData.getOrDefault("name", "");
                            String phone = (String) formData.getOrDefault("phone", "");
                            String cellPhone = (String) formData.getOrDefault("cellPhone", "");
                            byte[] photoData = (byte[]) formData.getOrDefault("photo", null);
                            
                            if (!name.isEmpty() && !phone.isEmpty() && !cellPhone.isEmpty()) {
                                Contact contact = new Contact(name, phone, cellPhone);
                                
                                // add photo if we have one
                                if (photoData != null && photoData.length > 0) {
                                    System.out.println("Adding photo for contact: " + name + " (size: " + photoData.length + " bytes)");
                                    contact.setPhoto(photoData);
                                    
                                    // quick check to make sure photo was saved
                                    byte[] savedPhoto = contact.getPhoto();
                                    if (savedPhoto != null) {
                                        System.out.println("Verified photo storage: " + savedPhoto.length + " bytes");
                                    } else {
                                        System.err.println("ERROR: Failed to save photo data!");
                                    }
                                }
                                
                                contacts.add(contact);
                                saveContacts();
                                
                                // redirect back with success message
                                exchange.getResponseHeaders().set("Location", "/?message=Contact+added+successfully");
                                exchange.sendResponseHeaders(302, -1);
                                return;
                            }
                        }
                    } else {
                        // simple form data without files
                        Map<String, String> formData = parseFormData(exchange.getRequestBody());
                        
                        String name = formData.getOrDefault("name", "");
                        String phone = formData.getOrDefault("phone", "");
                        String cellPhone = formData.getOrDefault("cellPhone", "");
                        
                        if (!name.isEmpty() && !phone.isEmpty() && !cellPhone.isEmpty()) {
                            contacts.add(new Contact(name, phone, cellPhone));
                            saveContacts();
                            
                            // redirect back with success message
                            exchange.getResponseHeaders().set("Location", "/?message=Contact+added+successfully");
                            exchange.sendResponseHeaders(302, -1);
                            return;
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error adding contact: " + e.getMessage());
                    e.printStackTrace();
                }
                
                // if we get here, something went wrong
                exchange.getResponseHeaders().set("Location", "/?message=Error+adding+contact");
                exchange.sendResponseHeaders(302, -1);
            } else {
                // only POST is allowed
                exchange.sendResponseHeaders(405, -1);
            }
        }
        
        // get the boundary for multipart forms
        private String extractBoundary(String contentType) {
            if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
                int boundaryIndex = contentType.indexOf("boundary=");
                if (boundaryIndex != -1) {
                    return contentType.substring(boundaryIndex + 9);
                }
            }
            return null;
        }
        
        // this is the tricky part - handling file uploads
        private Map<String, Object> parseMultipartForm(byte[] data, String boundary) throws IOException {
            Map<String, Object> formFields = new HashMap<>();
            
            try {
                // multipart forms start with -- and then the boundary
                String fullBoundary = "--" + boundary;
                
                // we need this to process the headers
                String dataStr = new String(data, StandardCharsets.ISO_8859_1);
                
                // find all the boundaries in the data
                int pos = 0;
                int boundaryLength = fullBoundary.length();
                List<Integer> positions = new ArrayList<>();
                
                while ((pos = dataStr.indexOf(fullBoundary, pos)) != -1) {
                    positions.add(pos);
                    pos += boundaryLength;
                }
                
                // process each part between boundaries
                for (int i = 0; i < positions.size() - 1; i++) {
                    int start = positions.get(i) + boundaryLength;
                    int end = positions.get(i + 1);
                    
                    // skip CRLF after boundary
                    if (start < dataStr.length() && dataStr.charAt(start) == '\r' && start + 1 < dataStr.length() && dataStr.charAt(start + 1) == '\n') {
                        start += 2;
                    }
                    
                    // find where headers end and content starts (double CRLF)
                    int headersEnd = dataStr.indexOf("\r\n\r\n", start);
                    if (headersEnd == -1 || headersEnd >= end) continue;
                    
                    String headers = dataStr.substring(start, headersEnd);
                    int contentStart = headersEnd + 4; // Skip \r\n\r\n
                    
                    // remove CRLF before next boundary
                    int contentEnd = end;
                    if (contentEnd - 2 >= contentStart && dataStr.charAt(contentEnd - 2) == '\r' && dataStr.charAt(contentEnd - 1) == '\n') {
                        contentEnd -= 2;
                    }
                    
                    // get the field name and check if it's a file
                    String fieldName = extractFieldName(headers);
                    boolean isFile = headers.contains("filename=\"") && !headers.contains("filename=\"\"");
                    
                    if (fieldName == null) continue;
                    
                    if (isFile) {
                        // for files, keep the raw bytes
                        byte[] fileData = new byte[contentEnd - contentStart];
                        System.arraycopy(data, contentStart, fileData, 0, fileData.length);
                        formFields.put(fieldName, fileData);
                        System.out.println("Found file for field: " + fieldName + ", size: " + fileData.length + " bytes");
                    } else {
                        // for regular fields, convert to string
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
        
        // find where the headers end
        private int findHeaderEnd(byte[] data, int start, int end) {
            for (int i = start; i < end - 3; i++) {
                if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                    return i + 4;
                }
            }
            return -1;
        }
        
        // get the field name from the headers
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

    // handles searching for contacts
    static class SearchContactHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // get the search query
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String searchQuery = params.getOrDefault("query", "").toLowerCase();
            
            // build HTML for search results
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
            
            // show matching contacts in a table
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
                    
                    // show image if available
                    response.append("<td>");
                    if (contact.hasPhoto()) {
                        // add timestamp to prevent browser caching
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
            
            // show message if no results found
            if (!foundResults) {
                response.append("<p>No contacts found matching \"").append(searchQuery).append("\"</p>");
            }
            
            response.append("</body></html>");

            // send the response
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.length());
            
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }

    // handles deleting contacts
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
                        
                        // redirect with success message
                        exchange.getResponseHeaders().set("Location", "/?message=Contact+deleted+successfully");
                        exchange.sendResponseHeaders(302, -1);
                        return;
                    }
                } catch (NumberFormatException e) {
                    // bad ID format - just ignore
                }
                
                // if we get here, something went wrong
                exchange.getResponseHeaders().set("Location", "/?message=Error+deleting+contact");
                exchange.sendResponseHeaders(302, -1);
            } else {
                // only POST is allowed
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // try to figure out what type of image we're dealing with
    private static String detectImageType(byte[] data) {
        if (data == null || data.length < 2) {
            System.out.println("Image data is too short or null");
            return "image/jpeg"; // just assume JPEG if we can't tell
        }
        
        // print the first few bytes for debugging
        System.out.println("Image signature bytes: " + 
                           String.format("%02X %02X %02X %02X", 
                                         data.length > 0 ? data[0] & 0xFF : 0, 
                                         data.length > 1 ? data[1] & 0xFF : 0,
                                         data.length > 2 ? data[2] & 0xFF : 0,
                                         data.length > 3 ? data[3] & 0xFF : 0));
        
        // JPEG starts with FF D8
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            System.out.println("Detected JPEG image");
            return "image/jpeg";
        }
        
        // PNG starts with 89 50 4E 47
        if (data.length >= 4 && 
            (data[0] & 0xFF) == 0x89 && 
            (data[1] & 0xFF) == 0x50 && 
            (data[2] & 0xFF) == 0x4E && 
            (data[3] & 0xFF) == 0x47) {
            System.out.println("Detected PNG image");
            return "image/png";
        }
        
        // GIF starts with 47 49 46
        if (data.length >= 3 && 
            (data[0] & 0xFF) == 0x47 && 
            (data[1] & 0xFF) == 0x49 && 
            (data[2] & 0xFF) == 0x46) {
            System.out.println("Detected GIF image");
            return "image/gif";
        }
        
        // just guess JPEG if we can't figure it out
        System.out.println("Unknown image format, defaulting to JPEG");
        return "image/jpeg";
    }
    
    // handles serving images for contacts
    static class ImageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // get the contact ID from the URL
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQueryParams(query);
            String idStr = params.getOrDefault("id", "");
            
            try {
                int id = Integer.parseInt(idStr);
                if (id >= 0 && id < contacts.size() && contacts.get(id).hasPhoto()) {
                    Contact contact = contacts.get(id);
                    byte[] imageData = contact.getPhoto();
                    
                    // print some debug info
                    System.out.println("Serving image for contact: " + contact.getName() + 
                                       " (ID: " + id + ") with image size: " + 
                                       (imageData != null ? imageData.length : 0) + " bytes");
                    
                    if (imageData == null || imageData.length == 0) {
                        // no image data
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    
                    // try to figure out what kind of image it is
                    String contentType = detectImageType(imageData);
                    
                    // add timestamp to prevent caching
                    String cacheBreaker = String.valueOf(System.currentTimeMillis());
                    
                    // set headers and send the image
                    exchange.getResponseHeaders().set("Content-Type", contentType);
                    exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
                    exchange.getResponseHeaders().set("Pragma", "no-cache");
                    exchange.getResponseHeaders().set("Expires", "0");
                    exchange.sendResponseHeaders(200, imageData.length);
                    
                    // send the actual image data
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(imageData);
                    }
                    return;
                }
            } catch (NumberFormatException e) {
                // bad ID format
                System.err.println("Invalid image ID: " + idStr);
            } catch (Exception e) {
                // other errors
                System.err.println("Error serving image: " + e.getMessage());
                e.printStackTrace();
            }
            
            // if we get here, something went wrong
            exchange.sendResponseHeaders(404, -1);
        }
    }
}