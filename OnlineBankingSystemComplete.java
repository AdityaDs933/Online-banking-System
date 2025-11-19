// File: OnlineBankingSystemComplete.java
// Requires H2 JDBC driver on classpath (download h2 jar from https://www.h2database.com)

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

// Main class
public class OnlineBankingSystemComplete {
    // JDBC connection URL - in-memory H2 database
    private static final String JDBC_URL = "jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1";
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASS = "";

    // DAOs & Service (initialized at runtime so we can fall back to in-memory implementations)
    private UserDAO userDao;
    private AccountDAO accountDao;
    private TransactionDAO txDao;
    private BankService bankService;

    private JFrame frame;
    private User currentUser;

    public static void main(String[] args) {
        // Detect H2 JDBC driver and initialize DB if available. If driver is missing, fall back to in-memory DAOs.
        boolean jdbcAvailable = true;
        try {
            Class.forName("org.h2.Driver");
        } catch (ClassNotFoundException ex) {
            jdbcAvailable = false;
            System.err.println("H2 driver not found on classpath. Falling back to in-memory mode.");
            JOptionPane.showMessageDialog(null, "H2 driver not found; running in-memory demo (no persistence)");
        }

        if (jdbcAvailable) {
            try { initDatabase(); }
            catch (SQLException e) { e.printStackTrace(); JOptionPane.showMessageDialog(null, "Failed to initialize DB: " + e.getMessage()); System.exit(1); }
        }

        final boolean useJdbc = jdbcAvailable;
        SwingUtilities.invokeLater(() -> {
            OnlineBankingSystemComplete app = new OnlineBankingSystemComplete(useJdbc);
            app.seedDemoData();
            app.showLogin();
        });
    }

    // constructor: choose DAO implementations based on runtime availability
    public OnlineBankingSystemComplete(boolean useJdbc) {
        if (useJdbc) {
            this.userDao = new JdbcUserDAO();
            this.accountDao = new JdbcAccountDAO();
            this.txDao = new JdbcTransactionDAO();
        } else {
            this.userDao = new InMemoryUserDAO();
            this.accountDao = new InMemoryAccountDAO();
            this.txDao = new InMemoryTransactionDAO();
        }
        this.bankService = new BankService(accountDao, txDao);
    }

    // ---------- DB init ----------
    private static void initDatabase() throws SQLException {
        try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
             Statement s = c.createStatement()) {
            s.execute("CREATE TABLE users (id IDENTITY PRIMARY KEY, name VARCHAR(100), email VARCHAR(150) UNIQUE, password VARCHAR(200), role VARCHAR(20))");
            s.execute("CREATE TABLE accounts (id IDENTITY PRIMARY KEY, owner VARCHAR(100), balance BIGINT, type VARCHAR(30), overdraft BIGINT, rate DOUBLE)");
            s.execute("CREATE TABLE transactions (id IDENTITY PRIMARY KEY, account_id BIGINT, type VARCHAR(30), amount BIGINT, ts TIMESTAMP, performed_by BIGINT, note VARCHAR(255))");
        }
    }

    // ---------- seed demo data ----------
    private void seedDemoData() {
        try {
            // create users if not exist
            if (userDao.findByEmail("admin@bank") == null) {
                userDao.save(new User("Admin", "admin@bank", "admin", Role.ADMIN));
                userDao.save(new User("Alice", "alice@bank", "alice", Role.CUSTOMER));
                userDao.save(new User("Bob", "bob@bank", "bob", Role.CUSTOMER));
            }
            // create accounts if none
            if (accountDao.listAll().isEmpty()) {
                Account a1 = new SavingsAccount("Alice", 1000L, 2.0);
                Account a2 = new CheckingAccount("Bob", 500L, 200L);
                accountDao.save(a1);
                accountDao.save(a2);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Seeding failed: " + ex.getMessage());
        }
    }

    // ---------- Login ----------
    private void showLogin() {
        JTextField email = new JTextField();
        JPasswordField pwd = new JPasswordField();
        final JComponent[] inputs = new JComponent[] {
                new JLabel("Email:"), email,
                new JLabel("Password:"), pwd
        };
        int result = JOptionPane.showConfirmDialog(null, inputs, "Login", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            System.exit(0);
        }
        String e = email.getText().trim();
        String p = new String(pwd.getPassword());

        try {
            User u = userDao.findByEmail(e);
            if (u == null || !u.getPassword().equals(p)) {
                JOptionPane.showMessageDialog(null, "Invalid credentials. Demo users:\nadmin@bank/admin\nalice@bank/alice\nbob@bank/bob");
                showLogin();
                return;
            }
            currentUser = u;
            SwingUtilities.invokeLater(this::buildAndShowMainFrame);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Login failed: " + ex.getMessage());
            showLogin();
        }
    }

    // ---------- Main frame ----------
    private void buildAndShowMainFrame() {
        frame = new JFrame("Banking System - " + currentUser.getName() + " (" + currentUser.getRole() + ")");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 700);
        frame.setLayout(new BorderLayout());

        if (currentUser.getRole() == Role.ADMIN) {
            frame.add(new AdminPanel(), BorderLayout.CENTER);
        } else {
            frame.add(new CustomerPanel(currentUser), BorderLayout.CENTER);
        }

        frame.setVisible(true);
    }

    // ---------- Admin Panel ----------
    private class AdminPanel extends JPanel {
        private final DefaultListModel<String> userListModel = new DefaultListModel<>();
        private final JTextArea txArea = new JTextArea();

        AdminPanel() {
            setLayout(new BorderLayout());
            JPanel top = new JPanel(new GridLayout(1,2));

            // user management
            JPanel userPanel = new JPanel(new BorderLayout());
            userPanel.setBorder(BorderFactory.createTitledBorder("User Management"));
            JList<String> usersList = new JList<>(userListModel);
            userPanel.add(new JScrollPane(usersList), BorderLayout.CENTER);

            JPanel userControls = new JPanel();
            JTextField nameFld = new JTextField(8);
            JTextField emailFld = new JTextField(10);
            JTextField pwdFld = new JTextField(8);
            JComboBox<Role> roleBox = new JComboBox<>(Role.values());
            JButton addUser = new JButton("Add User");
            JButton delUser = new JButton("Delete Selected");
            userControls.add(new JLabel("Name")); userControls.add(nameFld);
            userControls.add(new JLabel("Email")); userControls.add(emailFld);
            userControls.add(new JLabel("Pwd")); userControls.add(pwdFld);
            userControls.add(new JLabel("Role")); userControls.add(roleBox);
            userControls.add(addUser); userControls.add(delUser);
            userPanel.add(userControls, BorderLayout.SOUTH);

            addUser.addActionListener(e -> {
                String n = nameFld.getText().trim();
                String em = emailFld.getText().trim();
                String pw = pwdFld.getText().trim();
                Role r = (Role) roleBox.getSelectedItem();
                if (n.isEmpty() || em.isEmpty() || pw.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Provide name, email, password.");
                    return;
                }
                // background add
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() throws Exception {
                        userDao.save(new User(n, em, pw, r));
                        return null;
                    }
                    @Override protected void done() { refreshUserList(); }
                }.execute();
            });

            delUser.addActionListener(e -> {
                String sel = usersList.getSelectedValue();
                if (sel == null) return;
                String[] parts = sel.split(":");
                String em = parts[2].trim();
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() throws Exception {
                        userDao.deleteByEmail(em);
                        return null;
                    }
                    @Override protected void done() { refreshUserList(); }
                }.execute();
            });

            // transaction monitor
            JPanel txPanel = new JPanel(new BorderLayout());
            txPanel.setBorder(BorderFactory.createTitledBorder("Transaction Monitor"));
            txArea.setEditable(false);
            txPanel.add(new JScrollPane(txArea), BorderLayout.CENTER);
            JButton refreshTxBtn = new JButton("Refresh Transactions");
            txPanel.add(refreshTxBtn, BorderLayout.SOUTH);
            refreshTxBtn.addActionListener(e -> refreshTxList());

            top.add(userPanel);
            top.add(txPanel);
            add(top, BorderLayout.CENTER);

            JPanel bottom = new JPanel();
            bottom.setBorder(BorderFactory.createTitledBorder("System Settings"));
            bottom.add(new JLabel("Settings panel (placeholder) — extendable"));
            add(bottom, BorderLayout.SOUTH);

            refreshUserList();
            refreshTxList();
        }

        private void refreshUserList() {
            new SwingWorker<List<User>, Void>() {
                protected List<User> doInBackground() throws Exception { return userDao.listAll(); }
                protected void done() {
                    try {
                        List<User> users = get();
                        userListModel.clear();
                        for (User u : users) userListModel.addElement(u.getId() + " : " + u.getName() + " : " + u.getEmail() + " : " + u.getRole());
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }

        private void refreshTxList() {
            new SwingWorker<List<Transaction>, Void>() {
                protected List<Transaction> doInBackground() throws Exception { return txDao.listAll(); }
                protected void done() {
                    try {
                        List<Transaction> txs = get();
                        txArea.setText("");
                        for (Transaction t : txs) txArea.append(t.toString() + "\n");
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }
    }

    // ---------- Customer Panel ----------
    private class CustomerPanel extends JPanel {
        private final DefaultListModel<String> accountsModel = new DefaultListModel<>();
        private final DefaultListModel<String> txModel = new DefaultListModel<>();
        private final JTextArea log = new JTextArea();

        private final JTextField ownerField = new JTextField(10);
        private final JTextField initialField = new JTextField(8);
        private final JTextField idField = new JTextField(6);
        private final JTextField amountField = new JTextField(8);
        private final JTextField toAccountField = new JTextField(6); // for transfer

        CustomerPanel(User user) {
            setLayout(new BorderLayout());

            JPanel top = new JPanel(new GridLayout(2,1));
            JPanel p1 = new JPanel();
            p1.add(new JLabel("Owner:")); p1.add(ownerField);
            p1.add(new JLabel("Initial (whole):")); p1.add(initialField);
            JButton createSavings = new JButton("Create Savings");
            JButton createChecking = new JButton("Create Checking");
            p1.add(createSavings); p1.add(createChecking);

            JPanel p2 = new JPanel();
            p2.add(new JLabel("AccountId:")); p2.add(idField);
            p2.add(new JLabel("Amount (whole):")); p2.add(amountField);
            p2.add(new JLabel("ToAccount")); p2.add(toAccountField);
            JButton deposit = new JButton("Deposit");
            JButton withdraw = new JButton("Withdraw");
            JButton transfer = new JButton("Transfer");
            JButton list = new JButton("List Accounts");
            p2.add(deposit); p2.add(withdraw); p2.add(transfer); p2.add(list);

            top.add(p1); top.add(p2);
            add(top, BorderLayout.NORTH);

            JPanel center = new JPanel(new GridLayout(1,2));
            center.add(new JScrollPane(new JList<>(accountsModel)));
            center.add(new JScrollPane(new JList<>(txModel)));
            add(center, BorderLayout.CENTER);

            log.setEditable(false);
            add(new JScrollPane(log), BorderLayout.SOUTH);

            // handlers
            createSavings.addActionListener(e -> createSavings(user));
            createChecking.addActionListener(e -> createChecking(user));
            deposit.addActionListener(e -> doDeposit(user));
            withdraw.addActionListener(e -> doWithdraw(user));
            transfer.addActionListener(e -> doTransfer(user));
            list.addActionListener(e -> refreshAccounts(user));

            refreshAccounts(user);
            refreshTxHistory(user);
        }

        private void append(String s) { SwingUtilities.invokeLater(() -> log.append(s + "\n")); }

        private void createSavings(User user) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    String owner = ownerField.getText().trim();
                    if (owner.isEmpty()) owner = user.getName();
                    long init = parseLongOrThrow(initialField.getText().trim(), "Initial");
                    Account a = new SavingsAccount(owner, init, 2.0);
                    accountDao.save(a);
                    return null;
                }
                protected void done() {
                    try { get(); append("Created account."); refreshAccounts(user); }
                    catch (Exception e) { append("Create failed: " + e.getMessage()); }
                }
            }.execute();
        }

        private void createChecking(User user) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    String owner = ownerField.getText().trim();
                    if (owner.isEmpty()) owner = user.getName();
                    long init = parseLongOrThrow(initialField.getText().trim(), "Initial");
                    Account a = new CheckingAccount(owner, init, 500L);
                    accountDao.save(a);
                    return null;
                }
                protected void done() {
                    try { get(); append("Created account."); refreshAccounts(user); }
                    catch (Exception e) { append("Create failed: " + e.getMessage()); }
                }
            }.execute();
        }

        private void doDeposit(User user) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    int id = (int) parseLongOrThrow(idField.getText().trim(), "AccountId");
                    long amt = parseLongOrThrow(amountField.getText().trim(), "Amount");
                    bankService.deposit(id, amt, user.getId(), "Deposit by " + user.getName());
                    return null;
                }
                protected void done() {
                    try { get(); append("Deposit OK"); refreshAccounts(user); refreshTxHistory(user); }
                    catch (Exception e) { append("Deposit failed: " + e.getMessage()); }
                }
            }.execute();
        }

        private void doWithdraw(User user) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    int id = (int) parseLongOrThrow(idField.getText().trim(), "AccountId");
                    long amt = parseLongOrThrow(amountField.getText().trim(), "Amount");
                    bankService.withdraw(id, amt, user.getId(), "Withdraw by " + user.getName());
                    return null;
                }
                protected void done() {
                    try { get(); append("Withdraw OK"); refreshAccounts(user); refreshTxHistory(user); }
                    catch (ExecutionException ex) { append("Withdraw failed: " + ex.getCause().getMessage()); }
                    catch (Exception e) { append("Withdraw failed: " + e.getMessage()); }
                }
            }.execute();
        }

        private void doTransfer(User user) {
            new SwingWorker<Void, Void>() {
                protected Void doInBackground() throws Exception {
                    int from = (int) parseLongOrThrow(idField.getText().trim(), "FromAccountId");
                    int to = (int) parseLongOrThrow(toAccountField.getText().trim(), "ToAccountId");
                    long amt = parseLongOrThrow(amountField.getText().trim(), "Amount");
                    bankService.transferAtomic(from, to, amt, user.getId(), "Transfer by " + user.getName());
                    return null;
                }
                protected void done() {
                    try { get(); append("Transfer OK"); refreshAccounts(user); refreshTxHistory(user); }
                    catch (ExecutionException ex) { append("Transfer failed: " + ex.getCause().getMessage()); }
                    catch (Exception e) { append("Transfer failed: " + e.getMessage()); }
                }
            }.execute();
        }

        private void refreshAccounts(User user) {
            new SwingWorker<List<Account>, Void>() {
                protected List<Account> doInBackground() throws Exception {
                    return accountDao.listAll();
                }
                protected void done() {
                    try {
                        List<Account> all = get();
                        accountsModel.clear();
                        for (Account a : all) {
                            if (a.getOwner().equalsIgnoreCase(user.getName())) {
                                accountsModel.addElement(a.toString());
                            }
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }

        private void refreshTxHistory(User user) {
            new SwingWorker<List<Transaction>, Void>() {
                protected List<Transaction> doInBackground() throws Exception {
                    return txDao.listAllForOwnerName(user.getName(), accountDao);
                }
                protected void done() {
                    try {
                        List<Transaction> txs = get();
                        txModel.clear();
                        for (Transaction t : txs) txModel.addElement(t.toString());
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }.execute();
        }

        private long parseLongOrThrow(String s, String field) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(field + " must be whole number");
            }
        }
    }

    // ---------- Domain models ----------
    enum Role { ADMIN, CUSTOMER }

    static class User {
        private long id;
        private final String name;
        private final String email;
        private final String password;
        private final Role role;

        public User(String name, String email, String password, Role role) {
            this.name = name; this.email = email; this.password = password; this.role = role;
        }

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPassword() { return password; }
        public Role getRole() { return role; }

        @Override public String toString() { return id + ":" + name + ":" + email + ":" + role; }
    }

    static abstract class Account {
        private long id;
        protected String owner;
        protected long balance; // whole units
        public Account(String owner, long balance) { this.owner = owner; this.balance = balance; }
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getOwner() { return owner; }
        public long getBalance() { return balance; }

        public synchronized void deposit(long amt) {
            if (amt <= 0) throw new IllegalArgumentException("amount must be > 0");
            balance += amt;
        }

        public synchronized void withdraw(long amt) throws InsufficientFundsException {
            if (amt <= 0) throw new IllegalArgumentException("amount must be > 0");
            if (balance < amt) throw new InsufficientFundsException("Insufficient funds");
            balance -= amt;
        }

        @Override public String toString() { return String.format("%d: %s (balance=%d)", id, owner, balance); }
    }

    static class SavingsAccount extends Account {
        private final double rate;
        public SavingsAccount(String owner, long balance, double rate) { super(owner, balance); this.rate = rate; }
        @Override public String toString() { return String.format("%d: Savings - %s (balance=%d) rate=%.2f", getId(), owner, balance, rate); }
    }

    static class CheckingAccount extends Account {
        private final long overdraft;
        public CheckingAccount(String owner, long balance, long overdraft) { super(owner, balance); this.overdraft = overdraft; }
        @Override public synchronized void withdraw(long amt) throws InsufficientFundsException {
            if (amt <= 0) throw new IllegalArgumentException("amount must be > 0");
            if (balance + overdraft < amt) throw new InsufficientFundsException("Insufficient funds (including overdraft)");
            balance -= amt;
        }
        @Override public String toString() { return String.format("%d: Checking - %s (balance=%d) overdraft=%d", getId(), owner, balance, overdraft); }
    }

    static class Transaction {
        private long id;
        private final long accountId;
        private final String type;
        private final long amount;
        private final Timestamp ts;
        private final long performedBy;
        private final String note;
        public Transaction(long accountId, String type, long amount, long performedBy, String note) {
            this.accountId = accountId; this.type = type; this.amount = amount; this.performedBy = performedBy; this.note = note;
            this.ts = Timestamp.valueOf(LocalDateTime.now());
        }
        public void setId(long id) { this.id = id; }
        public long getAccountId() { return accountId; }
        @Override public String toString() {
            return String.format("%d | acc:%d | %s | %d | by:%d | %s", id, accountId, ts.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), amount, performedBy, type + " - " + note);
        }
    }

    // ---------- Custom exception ----------
    static class InsufficientFundsException extends Exception {
        InsufficientFundsException(String msg) { super(msg); }
    }

    // ---------- DAO interfaces ----------
    interface UserDAO {
        void save(User u) throws Exception;
        User findByEmail(String email) throws Exception;
        List<User> listAll() throws Exception;
        void deleteByEmail(String email) throws Exception;
    }

    interface AccountDAO {
        void save(Account a) throws Exception;
        void update(Account a) throws Exception;
        List<Account> listAll() throws Exception;
        Account findById(long id) throws Exception;
    }

    interface TransactionDAO {
        void save(Transaction t) throws Exception;
        List<Transaction> listAll() throws Exception;
        List<Transaction> listAllForOwnerName(String ownerName, AccountDAO accountDao) throws Exception;
    }

    // ---------- In-memory DAO fallbacks (used when H2 driver missing) ----------
    static class InMemoryUserDAO implements UserDAO {
        private final List<User> store = new ArrayList<>();
        private long nextId = 1;
        public void save(User u) { u.setId(nextId++); store.add(u); }
        public User findByEmail(String email) { for (User u : store) if (u.getEmail().equalsIgnoreCase(email)) return u; return null; }
        public List<User> listAll() { return new ArrayList<>(store); }
        public void deleteByEmail(String email) { store.removeIf(u -> u.getEmail().equalsIgnoreCase(email)); }
    }

    static class InMemoryAccountDAO implements AccountDAO {
        private final List<Account> store = new ArrayList<>();
        private long nextId = 1;
        public synchronized void save(Account a) { a.setId(nextId++); store.add(a); }
        public synchronized void update(Account a) {
            for (int i = 0; i < store.size(); i++) if (store.get(i).getId() == a.getId()) { store.set(i, a); return; }
            store.add(a);
        }
        public synchronized List<Account> listAll() { return new ArrayList<>(store); }
        public synchronized Account findById(long id) { for (Account a : store) if (a.getId() == id) return a; return null; }
    }

    static class InMemoryTransactionDAO implements TransactionDAO {
        private final List<Transaction> store = new ArrayList<>();
        private long nextId = 1;
        public synchronized void save(Transaction t) { t.setId(nextId++); store.add(t); }
        public synchronized List<Transaction> listAll() { List<Transaction> out = new ArrayList<>(store); out.sort(Comparator.comparing(tr -> tr.ts)); Collections.reverse(out); return out; }
        public synchronized List<Transaction> listAllForOwnerName(String ownerName, AccountDAO accountDao) throws Exception {
            List<Long> ids = new ArrayList<>();
            for (Account a : accountDao.listAll()) if (a.getOwner().equalsIgnoreCase(ownerName)) ids.add(a.getId());
            List<Transaction> out = new ArrayList<>();
            for (Transaction t : store) if (ids.contains(t.getAccountId())) out.add(t);
            out.sort(Comparator.comparing(tr -> tr.ts)); Collections.reverse(out); return out;
        }
    }

    // ---------- JDBC DAO implementations ----------
    static class JdbcUserDAO implements UserDAO {
        public void save(User u) throws SQLException {
            String sql = "INSERT INTO users (name, email, password, role) VALUES (?, ?, ?, ?)";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, u.getName());
                ps.setString(2, u.getEmail());
                ps.setString(3, u.getPassword());
                ps.setString(4, u.getRole().name());
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) u.setId(rs.getLong(1)); }
            }
        }

        public User findByEmail(String email) throws SQLException {
            String sql = "SELECT id, name, email, password, role FROM users WHERE LOWER(email)=LOWER(?)";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User u = new User(rs.getString("name"), rs.getString("email"), rs.getString("password"), Role.valueOf(rs.getString("role")));
                        u.setId(rs.getLong("id")); return u;
                    }
                }
            }
            return null;
        }

        public List<User> listAll() throws SQLException {
            List<User> out = new ArrayList<>();
            String sql = "SELECT id, name, email, password, role FROM users";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) {
                    User u = new User(rs.getString("name"), rs.getString("email"), rs.getString("password"), Role.valueOf(rs.getString("role")));
                    u.setId(rs.getLong("id")); out.add(u);
                }
            }
            return out;
        }

        public void deleteByEmail(String email) throws SQLException {
            String sql = "DELETE FROM users WHERE LOWER(email)=LOWER(?)";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, email); ps.executeUpdate();
            }
        }
    }

    static class JdbcAccountDAO implements AccountDAO {
        public void save(Account a) throws SQLException {
            String sql = "INSERT INTO accounts (owner, balance, type, overdraft, rate) VALUES (?, ?, ?, ?, ?)";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, a.getOwner());
                ps.setLong(2, a.getBalance());
                ps.setString(3, a instanceof SavingsAccount ? "SAVINGS" : "CHECKING");
                if (a instanceof CheckingAccount) ps.setLong(4, ((CheckingAccount) a).overdraft);
                else ps.setNull(4, Types.BIGINT);
                if (a instanceof SavingsAccount) ps.setDouble(5, ((SavingsAccount) a).rate);
                else ps.setNull(5, Types.DOUBLE);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) a.setId(rs.getLong(1)); }
            }
        }

        public void update(Account a) throws SQLException {
            String sql = "UPDATE accounts SET owner=?, balance=?, overdraft=?, rate=? WHERE id=?";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, a.getOwner());
                ps.setLong(2, a.getBalance());
                if (a instanceof CheckingAccount) ps.setLong(3, ((CheckingAccount) a).overdraft);
                else ps.setNull(3, Types.BIGINT);
                if (a instanceof SavingsAccount) ps.setDouble(4, ((SavingsAccount) a).rate);
                else ps.setNull(4, Types.DOUBLE);
                ps.setLong(5, a.getId());
                ps.executeUpdate();
            }
        }

        public List<Account> listAll() throws SQLException {
            List<Account> out = new ArrayList<>();
            String sql = "SELECT id, owner, balance, type, overdraft, rate FROM accounts";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    Account a;
                    if ("SAVINGS".equalsIgnoreCase(type))
                        a = new SavingsAccount(rs.getString("owner"), rs.getLong("balance"), rs.getDouble("rate"));
                    else
                        a = new CheckingAccount(rs.getString("owner"), rs.getLong("balance"), rs.getLong("overdraft"));
                    a.setId(rs.getLong("id"));
                    out.add(a);
                }
            }
            return out;
        }

        public Account findById(long id) throws SQLException {
            String sql = "SELECT id, owner, balance, type, overdraft, rate FROM accounts WHERE id = ?";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String type = rs.getString("type");
                        Account a;
                        if ("SAVINGS".equalsIgnoreCase(type))
                            a = new SavingsAccount(rs.getString("owner"), rs.getLong("balance"), rs.getDouble("rate"));
                        else
                            a = new CheckingAccount(rs.getString("owner"), rs.getLong("balance"), rs.getLong("overdraft"));
                        a.setId(rs.getLong("id"));
                        return a;
                    }
                }
            }
            return null;
        }
    }

    static class JdbcTransactionDAO implements TransactionDAO {
        public void save(Transaction t) throws SQLException {
            String sql = "INSERT INTO transactions (account_id, type, amount, ts, performed_by, note) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, t.getAccountId());
                ps.setString(2, t.type);
                ps.setLong(3, t.amount);
                ps.setTimestamp(4, t.ts);
                ps.setLong(5, t.performedBy);
                ps.setString(6, t.note);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) { if (rs.next()) t.setId(rs.getLong(1)); }
            }
        }

        public List<Transaction> listAll() throws SQLException {
            List<Transaction> out = new ArrayList<>();
            String sql = "SELECT id, account_id, type, amount, ts, performed_by, note FROM transactions ORDER BY ts DESC";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(sql)) {
                while (rs.next()) {
                    Transaction t = new Transaction(rs.getLong("account_id"), rs.getString("type"), rs.getLong("amount"), rs.getLong("performed_by"), rs.getString("note"));
                    t.setId(rs.getLong("id"));
                    out.add(t);
                }
            }
            return out;
        }

        public List<Transaction> listAllForOwnerName(String ownerName, AccountDAO accountDao) throws Exception {
            // find account ids for this owner
            List<Long> ids = new ArrayList<>();
            for (Account a : accountDao.listAll()) if (a.getOwner().equalsIgnoreCase(ownerName)) ids.add(a.getId());
            if (ids.isEmpty()) return Collections.emptyList();

            List<Transaction> out = new ArrayList<>();
            String sql = "SELECT id, account_id, type, amount, ts, performed_by, note FROM transactions WHERE account_id = ? ORDER BY ts DESC";
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
                 PreparedStatement ps = c.prepareStatement(sql)) {
                for (Long id : ids) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Transaction t = new Transaction(rs.getLong("account_id"), rs.getString("type"), rs.getLong("amount"), rs.getLong("performed_by"), rs.getString("note"));
                            t.setId(rs.getLong("id")); out.add(t);
                        }
                    }
                }
            }
            return out;
        }
    }

    // ---------- Service layer ----------
    static class BankService {
        private final AccountDAO accountDao;
        private final TransactionDAO txDao;
        // Simple executor for background tasks (if needed)
        private final ExecutorService executor = Executors.newCachedThreadPool();

        public BankService(AccountDAO accountDao, TransactionDAO txDao) {
            this.accountDao = accountDao;
            this.txDao = txDao;
        }

        // Deposit operation
        public void deposit(long accountId, long amount, long performedBy, String note) throws Exception {
            if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
            // load, modify, persist in DB (JdbcAccountDAO uses separate connections, so we do simple pattern)
            synchronized (getLock(accountId)) {
                Account a = accountDao.findById(accountId);
                if (a == null) throw new IllegalArgumentException("Account not found");
                a.deposit(amount);
                accountDao.update(a);
                Transaction t = new Transaction(accountId, "DEPOSIT", amount, performedBy, note);
                txDao.save(t);
            }
        }

        // Withdraw operation
        public void withdraw(long accountId, long amount, long performedBy, String note) throws Exception {
            if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
            synchronized (getLock(accountId)) {
                Account a = accountDao.findById(accountId);
                if (a == null) throw new IllegalArgumentException("Account not found");
                a.withdraw(amount);
                accountDao.update(a);
                Transaction t = new Transaction(accountId, "WITHDRAW", amount, performedBy, note);
                txDao.save(t);
            }
        }

        // Atomic transfer with DB transaction semantics
        public void transferAtomic(long fromId, long toId, long amount, long performedBy, String note) throws Exception {
            if (amount <= 0) throw new IllegalArgumentException("amount must be > 0");
            // For true DB transactions across multiple tables we open a single connection and execute steps with commit/rollback.
            try (Connection c = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS)) {
                try {
                    c.setAutoCommit(false);
                    // lock rows by selecting FOR UPDATE (H2 supports table-level locking with SELECT ... FOR UPDATE not fully; we'll do optimistic approach)
                    Account from = findAccountForUpdate(c, fromId);
                    Account to = findAccountForUpdate(c, toId);
                    if (from == null || to == null) throw new IllegalArgumentException("Account(s) not found");
                    // business check
                    if (from.balance < amount) throw new InsufficientFundsException("Insufficient funds in source account");
                    // apply
                    from.balance -= amount;
                    to.balance += amount;
                    // persist updated balances
                    updateAccountWithConn(c, from);
                    updateAccountWithConn(c, to);
                    // persist transactions
                    insertTxWithConn(c, new Transaction(fromId, "TRANSFER_OUT", amount, performedBy, note + " -> to:" + toId));
                    insertTxWithConn(c, new Transaction(toId, "TRANSFER_IN", amount, performedBy, note + " <- from:" + fromId));
                    c.commit();
                } catch (Exception ex) {
                    c.rollback();
                    throw ex;
                } finally {
                    c.setAutoCommit(true);
                }
            }
        }

        // helper: acquire per-account lock object
        private final ConcurrentMap<Long, Object> locks = new ConcurrentHashMap<>();
        private Object getLock(long id) { return locks.computeIfAbsent(id, k -> new Object()); }

        // helper for transfer DB operations
        private Account findAccountForUpdate(Connection c, long id) throws SQLException {
            String sql = "SELECT id, owner, balance, type, overdraft, rate FROM accounts WHERE id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    String type = rs.getString("type");
                    Account a = "SAVINGS".equalsIgnoreCase(type) ? new SavingsAccount(rs.getString("owner"), rs.getLong("balance"), rs.getDouble("rate"))
                                                                 : new CheckingAccount(rs.getString("owner"), rs.getLong("balance"), rs.getLong("overdraft"));
                    a.setId(rs.getLong("id"));
                    return a;
                }
            }
        }

        private void updateAccountWithConn(Connection c, Account a) throws SQLException {
            String sql = "UPDATE accounts SET owner=?, balance=?, overdraft=?, rate=? WHERE id=?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, a.getOwner());
                ps.setLong(2, a.getBalance());
                if (a instanceof CheckingAccount) ps.setLong(3, ((CheckingAccount) a).overdraft);
                else ps.setNull(3, Types.BIGINT);
                if (a instanceof SavingsAccount) ps.setDouble(4, ((SavingsAccount) a).rate);
                else ps.setNull(4, Types.DOUBLE);
                ps.setLong(5, a.getId());
                ps.executeUpdate();
            }
        }

        private void insertTxWithConn(Connection c, Transaction t) throws SQLException {
            String sql = "INSERT INTO transactions (account_id, type, amount, ts, performed_by, note) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, t.accountId);
                ps.setString(2, t.type);
                ps.setLong(3, t.amount);
                ps.setTimestamp(4, t.ts);
                ps.setLong(5, t.performedBy);
                ps.setString(6, t.note);
                ps.executeUpdate();
            }
        }
    }
}
