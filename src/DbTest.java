
import java.sql.Connection;
import java.sql.DriverManager;

public class DbTest {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/blood_bank_db";
        String user = "root";

        // Replace with your actual MySQL password
        String password = "mysql password";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection con = DriverManager.getConnection(
                url, user, password
            );

            System.out.println("SUCCESS! Java connected to MySQL.");
            System.out.println("Database: blood_bank_db");

            con.close();
        } catch (Exception e) {
            System.out.println("Connection failed!");
            System.out.println(e.getMessage());
        }
    }
}
