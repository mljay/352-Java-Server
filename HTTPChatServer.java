/* Notes: 
 * This code is modified from the original to work with 
 * the CS 352 chat client:
 *
 * 1. added args to allow for a command line to the port 
 * 2. Added 200 OK code to the sendResponse near line 77
 * 3. Changed default file name in getFilePath method to ./ from www 
 */ 
import java.io.*;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.lang.Math;

// Read the full article https://dev.to/mateuszjarzyna/build-your-own-http-server-in-java-in-less-than-one-hour-only-get-method-2k02
public class HTTPChatServer {

    public static void main( String[] args ) throws Exception {

	if (args.length != 1) 
        {
            System.err.println("Usage: java Server <port number>");
            System.exit(1);
        }
        //create server socket given port number
        int portNumber = Integer.parseInt(args[0]);
	
        try (ServerSocket serverSocket = new ServerSocket(portNumber)) {
            while (true) {
                try (Socket client = serverSocket.accept()) {
                    handleClient(client);
                }
            }
        }
    }

    private static void handleClient(Socket client) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));

        StringBuilder requestBuilder = new StringBuilder();
        String line;
        while (!(line = br.readLine()).isBlank()) {
            requestBuilder.append(line + "\r\n");
        }
        while(br.ready()){
            int a = br.read();
            char c = (char)a;
            line = line + c;
        }
        requestBuilder.append(line + "\r\n");

        String request = requestBuilder.toString();

        System.out.printf("The request is: %s \n", request);
        String username = "";
        String password = "";
        String clientCookie = "";
        String message = "";

	String[] requestsLines = request.split("\r\n");
    for(int i = 0; i < requestsLines.length; i++){
        if(requestsLines[i].contains("username")){
            String credentials = requestsLines[i];
            String[] logs = credentials.split("&");
            username = logs[0].substring(9);
            password = logs[1].substring(9);
        }
        if(requestsLines[i].contains("cookie_id")){
            String cook_id = requestsLines[i];
            clientCookie = cook_id.substring(18);
        }
        if(requestsLines[i].contains("message=")){
            String message_id = requestsLines[i];
            message = message_id.substring(8);
        }

    }
    System.out.println();
        String[] requestLine = requestsLines[0].split(" ");
        String method = requestLine[0];
        String path = requestLine[1];
        String version = requestLine[2];
        String host = requestsLines[1].split(" ")[1];

	// build the reponse here 
        List<String> headers = new ArrayList<>();
        for (int h = 2; h < requestsLines.length; h++) {
            String header = requestsLines[h];
            headers.add(header);
        }
        List<String> cookies = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        String cookie = "";
        Scanner scan = new Scanner(new File("allCookies.txt"));
        while(scan.hasNext()){
            String newline = scan.nextLine().toString();
            if(newline.length() > 6){
                cookies.add(newline);
            }
        }
        Scanner scan2 = new Scanner(new File("allMessages.txt"));
        while(scan2.hasNext()){
            String newline = scan2.nextLine().toString();
            if(newline.length() > 3){
                messages.add(newline);
            }
        }

        String accessLog = String.format("Client %s, method %s, path %s, version %s, host %s, headers %s",
                client.toString(), method, path, version, host, headers.toString());
        System.out.println("Access Logs: " + accessLog);

        if(method.contains("GET") && path.contains("login")){
            path = path.concat("login.html");
            getLogin(path, client);
        }
        else if(method.contains("GET") && path.contains("chat")){
            path = "/chat/chat.html";
            Path filePath = getFilePath(path);
            Scanner sc = new Scanner(filePath);
            //instantiating the StringBuffer class
            StringBuffer buffer = new StringBuffer();
            //Reading lines of the file and appending them to StringBuffer
            while (sc.hasNextLine()) {
                buffer.append(sc.nextLine()+System.lineSeparator());
            }
            String fileContents = buffer.toString();
            sc.close();
            String chatlogs = "<div id=\"chat-window\">";
            for (int i = 0; i < messages.size();i++) {
                chatlogs = chatlogs.concat("<p>");
                chatlogs = chatlogs.concat(messages.get(i));
                System.out.println("Message obtained");
                chatlogs = chatlogs.concat("<p>");
	        }   
            //Replacing the old line with new line
            fileContents = fileContents.replace("<div id=\"chat-window\">", chatlogs);

            byte[] strToBytes = fileContents.getBytes();

            Files.write(filePath, strToBytes);

            String read = Files.readAllLines(filePath).get(0);
            postChat(path, client);

        }
        else if(method.contains("POST") && path.contains("login")){
            String logs = "credentials.txt";
            path = "/chat/chat.html";
            //check if username and password match
            if(authenticate(logs, username, password)){
                int rand = (int)Math.random() * 1000;
                cookie = username.concat(String.valueOf(rand));
                cookies.add(cookie);
                postLogin(cookie, path, client);
            }
            else{
                //return error page
                path = "/login/error.html";
                sendError(path,client);
            }
        }
        else if(method.contains("POST") && path.contains("chat")){
            path = "/chat/chat.html";
            int match = 0;
            for (int i = 0; i < cookies.size();i++) {
                if(cookies.get(i).trim().contains(clientCookie)){
                    match = 1;
                }
	        }   
            if(match == 1){
                //add message to chat log
                postChat(path, client);
            }
        }
        if(!message.equals("")){
            try {
                FileWriter myWriter = new FileWriter("allMessages.txt");
                    String user = "";
                    for (int i = 0; i < cookies.size();i++) {
                        if(cookies.get(i).trim().contains(clientCookie)){
                            user = clientCookie.substring(0,5);
                        }
                    }   
                    myWriter.write(user + " : " + message);
                myWriter.close();
            }catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        } 
        if(!cookie.equals("")){
            try {
                FileWriter myWriter = new FileWriter("allCookies.txt");
                myWriter.write(username + ", " + cookie);
                myWriter.close();
            }catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
        }
        System.out.println("Done writing to client");
        
    }
    public static boolean authenticate(String fileName, String searchUser, String searchPwd) throws FileNotFoundException{
        Scanner scan = new Scanner(new File(fileName));
        while(scan.hasNext()){
            String line = scan.nextLine().toString();
            if(line.contains(searchUser) && line.contains(searchPwd)){
                return true;
            }
        }
        return false;
    }

    //return get login request from client
    private static void getLogin(String path, Socket client)throws IOException{
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // file exist
            String contentType = guessContentType(filePath);
            
            System.out.printf("GET Login\r\n");
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath));
        } else {
            // 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent);
        }
    }
    //return post login request from client
    private static void postLogin(String cookie, String path, Socket client)throws IOException{
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // file exist
            String contentType = guessContentType(filePath);
            System.out.printf("POST Login\r\n");
            sendCookieResponse(client, "200 OK", contentType, cookie, Files.readAllBytes(filePath));
        } else {
            // 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent);
        }
    }
    //Return post chat request from client
    private static void postChat(String path, Socket client)throws IOException{
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // file exist
            String contentType = guessContentType(filePath);
            System.out.printf("POST Chat Response\r\n");
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath));
        } else {
            // 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent);
        }
    }

    //Send error page to client
     private static void sendError(String path, Socket client)throws IOException{
        Path filePath = getFilePath(path);
        if (Files.exists(filePath)) {
            // file exist
            String contentType = guessContentType(filePath);
            System.out.println("Sending error string");
            sendResponse(client, "200 OK", contentType, Files.readAllBytes(filePath));
        } else {
            // 404
            byte[] notFoundContent = "<h1>Not found :(</h1>".getBytes();
            sendResponse(client, "404 Not Found", "text/html", notFoundContent);
        }
    }

    //Send error to client
    private static void sendResponse(Socket client, String status, String contentType, byte[] content) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 200 OK" + status + "\r\n").getBytes());
        clientOutput.write(("ContentType: " + contentType + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.flush();
        client.close();
    }

    //Send response containing cookie to client
    private static void sendCookieResponse(Socket client, String status, String contentType, String cookie, byte[] content) throws IOException {
        OutputStream clientOutput = client.getOutputStream();
        clientOutput.write(("HTTP/1.1 200 OK" + status + "\r\n").getBytes());
        clientOutput.write(("ContentType: " + contentType + "\r\n").getBytes());
        clientOutput.write(("Set-Cookie: " + cookie + "\r\n").getBytes());
        clientOutput.write("\r\n".getBytes());
        clientOutput.write(content);
        clientOutput.flush();
        client.close();
    }

    private static Path getFilePath(String path) {
        if ("/".equals(path)) {
            path = "/index.html";
        }

        return Paths.get("./", path);
    }

    private static String guessContentType(Path filePath) throws IOException {
        return Files.probeContentType(filePath);
    }

}
