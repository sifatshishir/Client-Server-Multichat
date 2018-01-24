package tcpserver;

import java.net.*;
import java.io.*;
import java.sql.*;
import java.util.*;

public class TCPServer implements Runnable {

    private ChatServerThread clients[] = new ChatServerThread[50];
    static private String clientsName[] = new String[50];
    static private String chatRoom[] = new String[50];
    private String passWord = "";
    private ServerSocket server = null;
    private Thread thread = null;
    private int clientCount = 0;
    static int chatRoomCount = 50;
    static String url = "jdbc:mysql://localhost:3306/multichat";
    static String ins = "";
    static Connection con = null;

    public TCPServer(int port) {
        try {
            System.out.println("Binding to port " + port + ", please wait  ...");
            server = new ServerSocket(port);
            System.out.println("Server started: " + server);
            start();
        } catch (IOException ioe) {
            System.out.println("Can not bind to port " + port + ": " + ioe.getMessage());
        }
    }

    @Override
    public void run() {
        while (thread != null) {
            try {
                System.out.println("Waiting for a client ...");
                addThread(server.accept());
                //System.out.println(clientCount);               
            } catch (IOException ioe) {
                System.out.println("Server accept error: " + ioe);
                stop();
            }
        }
    }

    static public void init_instructions() throws FileNotFoundException {
        try {

            String dirname = "instructions.txt";
            BufferedReader input = new BufferedReader(new FileReader(dirname));
            while (input.ready()) {
                ins += input.readLine();
                ins += "\n";
            }

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    void show_instructions(int ID) {
        clients[findClient(ID)].send(ins + "\n");
    }

    static public void init_chatroom() {
        for (int i = 0; i < chatRoomCount; i++) {
            chatRoom[i] = "global";
        }
    }

    public void start() {
        if (thread == null) {
            thread = new Thread(this);
            thread.start();
        }
    }

    public void stop() {
        if (thread != null) {
            thread.stop();
            thread = null;
        }
    }

    private int findClient(int ID) {
        for (int i = 0; i < clientCount; i++) {
            if (clients[i].getID() == ID) {
                return i;
            }
        }
        return -1;
    }

    private int findClientbyName(String name) {
        for (int i = 0; i < clientCount; i++) {
            if (clientsName[i] == null) {
                continue;
            }
            if (clientsName[i].equals(name)) {
                //System.out.println(clientsName[i]);
                return i;
            }
        }
        return -1;
    }

    void leave_chatroom(int ID, String chatRoomName) throws SQLException {
        try {
            chatRoom[findClient(ID)] = "global";
            Statement state = con.createStatement();
            state.executeUpdate("UPDATE client SET room = '" + "global" + "' WHERE name = '" + clientsName[findClient(ID)] + "'");
            clients[findClient(ID)].send("You left from the chatroom " + chatRoomName + "\n");
            //state.executeUpdate("INSERT INTO client(name,password,room) VALUES('" + clientsName[findClient(ID)] + "','" + passWord + "','" + "global" + "')");
            state.close();
            for (int i = 0; i < chatRoomCount; i++) {
                if (chatRoom[i].equals(chatRoomName)) {
                    clients[i].send(clientsName[findClient(ID)] + " left the chatroom\n");
                }
            }
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    void join_chat_room(int ID, String chatRoomName) throws SQLException {
        try {
            if ((chatRoom[findClient(ID)] == null ? chatRoomName != null : !chatRoom[findClient(ID)].equals(chatRoomName)) && !"global".equals(chatRoom[findClient(ID)])) {
                String logged_out_room = chatRoom[findClient(ID)];

                if (!"global".equals(logged_out_room)) { // if not global room
                    leave_chatroom(ID, logged_out_room); // leave currecnt room for entering another room
                }
                for (int i = 0; i < chatRoomCount; i++) {
                    if (chatRoom[i].equals(logged_out_room) && !"global".equals(logged_out_room)) {
                        clients[i].send(clientsName[findClient(ID)] + " logged out from " + logged_out_room);
                    }
                }
            }
            for (int i = 0; i < chatRoomCount; i++) {
                if (chatRoom[i].equals(chatRoomName)) {
                    clients[i].send(clientsName[findClient(ID)] + " logged in to " + chatRoomName
                            + "\nEveryone welcome " + clientsName[findClient(ID)]);
                }
            }
            clients[findClient(ID)].send(clientsName[findClient(ID)] + " entered into chat room: " + chatRoomName);
            chatRoom[findClient(ID)] = chatRoomName; // logged in chatroom
            Statement state = con.createStatement();
            state.executeUpdate("UPDATE client SET room = '" + chatRoomName + "' WHERE name = '" + clientsName[findClient(ID)] + "'");
            //state.executeUpdate("INSERT INTO client(name,password,room) VALUES('" + clientsName[findClient(ID)] + "','" + passWord + "','" + chatRoomName + "')");
            state.close();

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    void show_all_chatrooms(int ID) throws SQLException {
        try {
            String msg = "";
            int pos = 1;
            Set<String> rooms = new HashSet<String>();

            Statement state = con.createStatement();
            ResultSet result = state.executeQuery("SELECT *FROM client");

            String current_room[] = new String[50];
            int counter = 0;
            while (result.next()) {
                String room = result.getString("room");
                current_room[counter++] = room;
            }
            state.close();

            // remove duplicate rooms by set
            for (int i = 0; i < counter; i++) {
                if (!"global".equals(current_room[i])) {
                    rooms.add(current_room[i]);
                }
            }
            for (String room : rooms) {
                msg += String.valueOf(pos) + ": " + room + "\n";
                pos++;
            }
            if (msg == "") {
                msg = "There is no chat room available.\n"
                        + "Type joinchatroom 'Chat_Room_Name' to join a chat room\n";
            }
            clients[findClient(ID)].send(msg);
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    // show client's friend from database
    void show_friend_list(int ID) throws SQLException {
        try {
            String name = clientsName[findClient(ID)];
            Statement state = con.createStatement();

            ResultSet result = state.executeQuery("SELECT list FROM friends WHERE source = '" + name + "'");
            int pos = 1;
            String msg = "";
            while (result.next()) {
                name = result.getString("list");
                msg += String.valueOf(pos) + ": " + name + "\n";
                pos++;
            }
            if (pos == 1) {
                msg = "You currently have no friend";
            }
            msg += "\n";
            clients[findClient(ID)].send(msg);
            state.close();
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    // logged in clients shows to specific user
    void show_clients_online(int ID) {
        String msg = "";
        for (int i = 0; i < clientCount; i++) {
            if (clientsName[i] != null) {
                msg += String.valueOf(i + 1) + ": " + clientsName[i] + "\n";
            }
        }
        msg += "\n";
        clients[findClient(ID)].send(msg);
    }

    // create in the database
    void create_user(int ID, String input) throws SQLException {
        try {
            Integer nameId = findClient(ID);
            String str[] = new String[100];
            str = input.split(" ");
            String msg = "";
            Statement state = con.createStatement();
            ResultSet result = null;
            String create_user = "INSERT INTO client(name,password,room) VALUES('" + str[1] + "','" + str[2] + "','" + "global" + "')";
            state.executeUpdate(create_user);
            msg = "Account creation successfull!!\n";
            msg += "Hello " + str[1] + "\n";
            msg += "Please log in first\n";
            clients[findClient(ID)].send(msg);
            state.close();
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    boolean check_existance_of_user(String username) throws SQLException {
        try {
            Statement state = con.createStatement();
            ResultSet result = null;
            result = state.executeQuery("SELECT *FROM client");

            while (result.next()) {
                String name = result.getString("name");
                if (name.equals(username)) {
                    state.close();
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
        return false;
    }

    void pending_messages(int ID, String name) throws SQLException {
        try {

            Statement state = con.createStatement();
            ResultSet result = null;
            result = state.executeQuery("SELECT *FROM message");

            while (result.next()) {
                String rcvfrom = result.getString("receivedfrom");
                String msg = result.getString("msg");
                clients[findClient(ID)].send("Message from user: " + rcvfrom + "\n" + msg + "\n");
            }
            // delete after reading
            state.executeUpdate("DELETE FROM message WHERE name = '" + clientsName[findClient(ID)] + "'");
            state.close();

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    void show_friend_request(int ID) throws SQLException {
        try {
            String name = clientsName[findClient(ID)];
            Statement state = con.createStatement();
            ResultSet result = state.executeQuery("SELECT *FROM friend_request");
            int f = 0;
            while (result.next()) {
                f = 1;
                String req_from = result.getString("name");
                String username = result.getString("request");
                if (username.equals(name)) {
                    clients[findClient(ID)].send(req_from + " wants to be friend with you\n");
                }
            }
            state.close();
            if (f == 0) {
                clients[findClient(ID)].send("You have no friend requests\n");
            } else {
                clients[findClient(ID)].send("Type 'yes' or 'no' then friend's name to accept or reject\n");
            }
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    void pending_requests(int ID, String name) throws SQLException {
        try {

            Statement state = con.createStatement();
            ResultSet result = state.executeQuery("SELECT name FROM friend_request WHERE request = '" + name + "'");
            while (result.next()) {
                String req_from = result.getString("name");
                clients[findClient(ID)].send(req_from + " wants to be friend with you\n");
            }
            state.close();
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    // login
    void login_user(int ID, String input) throws SQLException {
        try {
            String str[] = new String[100];
            str = input.split(" ");
            String msg = "";

            Integer nameId = findClient(ID);
            if (str.length < 3) {
                clients[nameId].send("Invalid command\nType 'INS'\n");
                return;
            }

            if (clientsName[nameId] != null) { // if already logged in
                if (clientsName[nameId].equals(str[1])) {
                    clients[nameId].send("You are already logged in!\n");
                    return;
                } else {
                    clients[nameId].send(clientsName[nameId] + " already logged in!\nPlease logout first\n");
                    return;
                }
            }

            Statement state = con.createStatement();
            ResultSet result = null;
            result = state.executeQuery("SELECT *FROM client");
            String room = "global";
            int found = 0;
            while (result.next()) {
                String name = result.getString("name");
                String pass = result.getString("password");
                room = result.getString("room");
                if (name.equals(str[1]) && pass.equals(str[2])) { // login confirmed
                    System.out.println("Welcome " + str[1]);
                    passWord = pass;
                    found = 1;
                    break;
                }
            }
            state.close();
            if (found == 0) { // if not proper usernmae or password
                clients[nameId].send("Incorrect Username or Password\nType 'INS'\n");
            } else {

                // check for current logged in username
                if (nameId == -1) { // if no name, insert name for currently user
                    nameId = findClient(ID);
                }

                clientsName[nameId] = str[1];
                clients[findClient(ID)].send("Welcome " + str[1] + "\n");
                chatRoom[nameId] = room;

                // check for pending message
                pending_messages(ID, str[1]);

                // check for pending friend requests
                pending_requests(ID, str[1]);

            }

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    boolean check_friendship(String name1, String name2) throws SQLException {
        try {
            Statement state = con.createStatement();
            ResultSet result = state.executeQuery("SELECT list FROM friends WHERE source = '" + name1 + "'");
            while (result.next()) {
                String name = result.getString("list");
                if (name.equals(name2)) {
                    return true;
                }
            }
            state.close();

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
        return false;
    }

    void make_friends(String name1, String name2) { // create friendship
        try {
            Statement state = con.createStatement();
            state.executeUpdate("INSERT INTO friends(source,list) VALUES('" + name1 + "','" + name2 + "')");
            state.executeUpdate("INSERT INTO friends(source,list) VALUES('" + name2 + "','" + name1 + "')");
            state.close();
            if (findClientbyName(name1) != -1) {
                clients[findClientbyName(name1)].send("You and " + name2 + " are friends now!!");
            } else {
                send_message(name2, name1, "Accepted your friend request", 0);
            }
            if (findClientbyName(name2) != -1) {
                clients[findClientbyName(name2)].send("You and " + name1 + " are friends now!!");
            } else {
                send_message(name1, name2, "Accepted your friend request", 0);
            }
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    boolean is_friends_with(String name1, String name2) throws SQLException { //check if already friend
        try {
            if (check_friendship(name1, name2)) {
                clients[findClientbyName(name1)].send("You are already friends with " + name2);
                //clients[findClientbyName(name2)].send("You are already friends with " + name1);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
        return false;
    }

    void send_message(String userName, String friend, String msg, int req) throws SQLException {
        try {

            if (check_friendship(userName, friend) || req == 1) {
                int index = findClientbyName(friend);
                if (index != -1) {
                    clients[index].send("message from " + userName + "\n" + msg);
                } else {
                    clients[findClientbyName(userName)].send(friend + " is offline now.");
                    Statement state = con.createStatement();
                    state.executeUpdate("INSERT INTO message(name,receivedfrom,msg) VALUES('" + friend + "','" + userName + "','" + msg + "')");
                    state.close();
                }
                clients[findClientbyName(userName)].send("Message successfully sent to user: " + friend + "\n");
            } else {
                clients[findClientbyName(userName)].send("You don't have anyone named: " + friend + "\nMake a friendship first\n");
            }
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    boolean check_already_sent_request(String username, String friend) throws SQLException {
        try {
            Statement state = con.createStatement();
            ResultSet result = state.executeQuery("SELECT *FROM friend_request WHERE name = '" + username + "'");
            while (result.next()) {
                String requestedname = result.getString("request");
                if (friend.equals(requestedname)) {
                    state.close();
                    return true;
                }
            }
            state.close();
            return false;

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
        return false;
    }

    void send_friend_request(int ID, String frndname) throws SQLException {
        try {
            String username = clientsName[findClient(ID)];

            if (username.equals(frndname)) { // same names
                clients[findClient(ID)].send("You can't send request to yourself!!!\n");
                return;
            }

            if (check_friendship(username, frndname)) { //already friends
                clients[findClient(ID)].send("You are already friends with " + frndname + "\n");
                return;
            }

            if (check_existance_of_user(frndname)) { // if there is an user in client

                if (check_already_sent_request(username, frndname)) { // if already sent request
                    clients[findClient(ID)].send("You already sent " + frndname + " a friend request!!\n");
                    return;
                }
                clients[findClient(ID)].send("You sent a friend request to " + frndname + "\n");

                Statement state = con.createStatement();
                state.executeUpdate("INSERT INTO friend_request(name,request) VALUES('" + username + "','" + frndname + "')");
                state.close();

                if (findClientbyName(frndname) != -1) { // user online
                    clients[findClientbyName(frndname)].send(username + " wants to be friend with you\nAccpet? type 'yes', else 'no' 'friend's name'\n");
                }

            } else {
                clients[findClient(ID)].send("There is no user named : " + frndname + "\n" + "Please write username properly\n");
                return;
            }

        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    boolean friend_req_validity_check(String name, String friend) throws SQLException {
        try {
            Statement state = con.createStatement();
            ResultSet result = state.executeQuery("SELECT *FROM friend_request");
            int p = 0;
            while (result.next()) {
                String user = result.getString("request");
                String req = result.getString("name");
                if (user.equals(name) && req.equals(friend)) {
                    p = 1;
                    break;
                }
            }
            if (p == 1) {
                state.executeUpdate("DELETE FROM friend_request WHERE name = '" + friend + "' AND request = '" + name + "'");
            }
            state.close();
            if (p == 1) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
        return false;
    }

    public synchronized void handle(int ID, String input) throws SQLException {
        //show_clients_online();
        try {

            Integer nameId = findClient(ID);
            System.out.println("ID: " + nameId + " Name: " + clientsName[nameId]);
            String str[] = new String[100];
            str = input.split(" ");
            String msg = "";
            if (str[0].equals("INS")) { // show instructions from file

                show_instructions(ID);

            } else if (str[0].equals("showrequest")) {
                show_friend_request(ID);
            } else if (str[0].equals("yes")) { // accept friend request
                int index = findClientbyName(str[1]);

                if (!check_existance_of_user(str[1])) {
                    clients[nameId].send("Please input yout friend's name correctly\n");

                } else {
                    if (!friend_req_validity_check(clientsName[nameId], str[1])) {
                        clients[nameId].send("Invalid command\n");
                        return;
                    }
                    make_friends(clientsName[nameId], str[1]); //become friends
                }

            } else if (str[0].equals("no")) {
                int index = findClientbyName(str[1]);
                if (!check_existance_of_user(str[1])) {
                    clients[nameId].send("Please input yout friend's name correctly\n");

                } else {
                    if (!friend_req_validity_check(clientsName[nameId], str[1])) { //if no req
                        clients[nameId].send("Invalid command\n");
                        return;
                    }
                    msg = "Rejected your friend request\n";
                    if (index == -1) { // send pending message

                        send_message(clientsName[nameId], str[1], msg, 1);
                    } else { // send online message
                        clients[findClientbyName(str[1])].send(clientsName[nameId] + " " + msg);
                    }
                }
            } else if (str[0].equals("connect")) { // send friend request
                send_friend_request(ID, str[1]);

            } else if (str[0].equals("message")) { // send message
                for (int i = 2; i < str.length; i++) {
                    msg += " " + str[i];
                }

                send_message(clientsName[nameId], str[1], msg, 0);

            } else if (str[0].equals("showuser")) { // show all user online
                show_clients_online(ID);

            } else if (str[0].equals("showfriend")) { // show client's friends
                show_friend_list(ID);

            } else if (str[0].equals("joinchatroom")) { //join a chat room
                if (clientsName[nameId] == null) {
                    clients[nameId].send("Please log in first\n");
                } else {
                    for (int i = 1; i < str.length; i++) { // chatroom name can have space
                        msg += " " + str[i];
                    }
                    join_chat_room(ID, msg);
                }

            } else if (str[0].equals("leavechatroom")) {
                if (chatRoom[nameId].equals("global")) { // global
                    clients[nameId].send("Please join a chatroom first!!\n");
                } else {
                    leave_chatroom(ID, chatRoom[nameId]); // leave from chatroom

                }

            } else if (str[0].equals("showchatroom")) { //show available chatrooms
                if (clientsName[nameId] == null) {
                    clients[findClient(ID)].send("Please log in first\n");
                } else {
                    show_all_chatrooms(ID);
                }

            } else if (str[0].equals("create")) { //create user
                create_user(ID, input);

            } else if (str[0].equals("login")) { // login client
                login_user(ID, input);

            } else if (str[0].equals("logout")) { // client logout

                clients[nameId].send("logout");
                clients[nameId].send("You logged out\n");
                remove(ID);

            } else {

                input += "( " + chatRoom[nameId] + " )";

                if (clientsName[nameId] != null) {
                    if (chatRoom[nameId].equals("global")) {
                        for (int i = 0; i < clientCount; i++) {
                            if (chatRoom[i].equals("global")) {
                                clients[i].send(clientsName[nameId] + ": " + input);
                            }
                        }
                    } else {
                        for (int i = 0; i < chatRoomCount; i++) {
                            if (chatRoom[i].equals(chatRoom[nameId])) {
                                clients[i].send(clientsName[nameId] + ": " + input);
                            }
                        }
                    }

                } else {
                    clients[findClient(ID)].send("Please log in first\n");
                }
            }
        } catch (Exception e) {
            System.err.println("Got an exception!");
            System.err.println(e.getMessage());
        }
    }

    public synchronized void remove(int ID) {
        int pos = findClient(ID);

        if (pos >= 0) {
            ChatServerThread toTerminate = clients[pos];
            System.out.println("Removing client thread " + ID + " at " + pos);
            System.out.println("ID: " + findClient(ID) + "," + clientsName[findClient(ID)] + " logging out....\n");
            clientsName[findClient(ID)] = null;

            if (pos < clientCount - 1) {
                for (int i = pos + 1; i < clientCount; i++) {
                    clients[i - 1] = clients[i];
                }
            }
            clientCount--;
            try {
                toTerminate.close();
            } catch (IOException ioe) {
                System.out.println("Error closing thread: " + ioe);
            }
            toTerminate.stop();
        }
    }

    private void addThread(Socket socket) {
        if (clientCount < clients.length) {
            System.out.println("Client accepted: " + socket);
            clients[clientCount] = new ChatServerThread(this, socket);
            try {
                clients[clientCount].open();
                clients[clientCount].start();
                clientCount++;
            } catch (IOException ioe) {
                System.out.println("Error opening thread: " + ioe);
            }
        } else {
            System.out.println("Client refused: maximum " + clients.length + " reached.");
        }
    }

    public static void main(String args[]) throws SQLException, FileNotFoundException {
        TCPServer server = null;
        server = new TCPServer(2000);

        Properties prop = new Properties();
        prop.setProperty("user", "root");
        prop.setProperty("password", "");
        Driver driver = new com.mysql.jdbc.Driver();
        con = driver.connect(url, prop);
        if (con == null) {
            System.out.println("connection failed");
            return;
        }
        init_chatroom(); // make all rooms global
        init_instructions();
    }
}
