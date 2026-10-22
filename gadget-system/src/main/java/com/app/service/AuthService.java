package com.app.service;

import com.app.dao.UserDAO;
import com.app.model.User;
import com.app.util.PasswordUtil;
import com.app.util.Transaction;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Authentication, registration and Owner-level user account management. */
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.]{3,30}$");
    private static final int MIN_PASSWORD = 6;

    private final UserDAO userDAO;
    private final ActivityLogService logService;

    public AuthService(UserDAO userDAO, ActivityLogService logService) {
        this.userDAO = userDAO;
        this.logService = logService;
    }

    // ---------------------------------------------------------------- session

    public User login(String username, String password) {
        User u = userDAO.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new ServiceException("Invalid username or password."));
        if (!PasswordUtil.matches(password, u.getPassword())) {
            throw new ServiceException("Invalid username or password.");
        }
        boolean usable = User.STATUS_ACTIVE.equalsIgnoreCase(u.getStatus())
                || User.STATUS_APPROVED.equalsIgnoreCase(u.getStatus());
        if (!usable) {
            if (User.STATUS_PENDING.equalsIgnoreCase(u.getStatus())) {
                throw new ServiceException("This account is still PENDING. Please wait for the Owner's approval.");
            }
            throw new ServiceException("This account is inactive. Please contact the Owner.");
        }
        if (!PasswordUtil.isHashed(u.getPassword())) { // transparently upgrade legacy plain-text passwords
            String hash = PasswordUtil.hash(password);
            Transaction.run(c -> userDAO.updatePassword(c, u.getUserId(), hash));
        }
        logService.log(u.getUserId(), "USER_LOGIN", u.getName() + " (" + u.getRole() + ") logged in.");
        u.setPassword(null); // never keep the credential in the session object
        return u;
    }

    public void logout(User u) {
        logService.log(u.getUserId(), "USER_LOGOUT", u.getName() + " logged out.");
    }

    /** True when no accounts exist yet (first run — next registration becomes Owner). */
    public boolean isFirstUser() {
        return userDAO.findAll().isEmpty();
    }

    // ----------------------------------------------------------- registration

    /**
     * Public self-registration (Main Entry Menu -> Sign Up) without a role choice:
     * the account is created Undefined + Pending and cannot log in until the
     * Owner assigns a role and activates it. Exception: the very first account
     * in an empty database becomes the active Owner (bootstrap, otherwise no
     * Owner could ever exist to approve anyone).
     */
    public User register(String name, String username, String password) {
        boolean firstUser = userDAO.findAll().isEmpty();
        String role = firstUser ? User.ROLE_OWNER : User.ROLE_UNDEFINED;
        String status = firstUser ? User.STATUS_ACTIVE : User.STATUS_PENDING;
        return createUser(null, name, username, password, role, status);
    }

    /**
     * Public self-registration WITH role selection (all roles in {@link User#ROLES}
     * are pickable). The account is stored with the chosen role and a Pending
     * status and cannot log in until the Owner activates it. Exception: the very
     * first account in an empty database always becomes the active Owner.
     */
    public User register(String name, String username, String password, String role) {
        boolean firstUser = userDAO.findAll().isEmpty();
        if (firstUser) {
            return createUser(null, name, username, password, User.ROLE_OWNER, User.STATUS_ACTIVE);
        }
        validateRole(role);
        return createUser(null, name, username, password, role.trim(), User.STATUS_PENDING);
    }

    /** Owner-created staff account. */
    public User createStaff(User actor, String name, String username, String password, String role) {
        AccessControl.require(actor, User.ROLE_OWNER);
        return createUser(actor, name, username, password, role, User.STATUS_ACTIVE);
    }

    private User createUser(User actor, String name, String username, String password, String role, String status) {
        String n = required(name, "Full name");
        String un = required(username, "Username");
        validatePassword(password);
        // "Undefined" is only ever issued by public self-registration above;
        // every explicitly chosen role (owner create/edit) is strictly validated.
        if (actor != null || !User.ROLE_UNDEFINED.equals(role)) validateRole(role);
        if (!USERNAME.matcher(un).matches()) {
            throw new ServiceException("Username must be 3-30 characters (letters, digits, '_' or '.').");
        }
        if (userDAO.findByUsername(un).isPresent()) throw new ServiceException("Username '" + un + "' is already taken.");

        User u = new User(n, un, PasswordUtil.hash(password), role, status);
        int id = Transaction.execute(c -> {
            int newId = userDAO.insert(c, u);
            if (actor == null) {
                logService.log(c, newId, "USER_REGISTER", "New account registered: " + un + " (" + role + ", " + status + ").");
            } else {
                logService.log(c, actor.getUserId(), "USER_CREATE", "Created account #" + newId + " " + un + " (" + role + ").");
            }
            return newId;
        });
        u.setUserId(id);
        u.setPassword(null);
        return u;
    }

    // ------------------------------------------------- Owner user management

    public List<User> listUsers(User actor) {
        AccessControl.require(actor, User.ROLE_OWNER);
        return userDAO.getAllUsers();
    }

    /** Every account awaiting approval, across all roles. */
    public List<User> pendingUsers(User actor) {
        AccessControl.require(actor, User.ROLE_OWNER);
        return userDAO.getPendingUsers();
    }

    /**
     * Approves one pending registration. The target id must currently sit in
     * the pending list; anything else (unknown id, already usable account)
     * is rejected instead of silently updated.
     */
    public User approveUser(User actor, int userId) {
        AccessControl.require(actor, User.ROLE_OWNER);
        User target = userDAO.getPendingUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst()
                .orElseThrow(() -> new ServiceException("Account #" + userId + " is not in the pending list."));
        Transaction.run(c -> {
            userDAO.approveUser(c, userId);
            logService.log(c, actor.getUserId(), "USER_APPROVE",
                    "Approved account #" + userId + " (" + target.getUsername() + ", " + target.getRole() + ").");
        });
        target.setStatus(User.STATUS_APPROVED);
        return target;
    }

    public void updateUser(User actor, int userId, String name, String role) {
        AccessControl.require(actor, User.ROLE_OWNER);
        User target = find(userId);
        String n = required(name, "Full name");
        validateRole(role);
        if (target.getUserId() == actor.getUserId() && !User.ROLE_OWNER.equals(role)) {
            throw new ServiceException("You cannot remove your own Owner role.");
        }
        target.setName(n);
        target.setRole(role);
        Transaction.run(c -> {
            userDAO.update(c, target);
            logService.log(c, actor.getUserId(), "USER_UPDATE", "Updated account #" + userId + " -> " + n + " (" + role + ").");
        });
    }

    public void setUserStatus(User actor, int userId, boolean active) {
        AccessControl.require(actor, User.ROLE_OWNER);
        User target = find(userId);
        if (!active && target.getUserId() == actor.getUserId()) {
            throw new ServiceException("You cannot deactivate your own account.");
        }
        String status = active ? User.STATUS_ACTIVE : User.STATUS_INACTIVE;
        Transaction.run(c -> {
            userDAO.updateStatus(c, userId, status);
            logService.log(c, actor.getUserId(), active ? "USER_ACTIVATE" : "USER_DEACTIVATE",
                    (active ? "Reactivated" : "Deactivated") + " account #" + userId + " (" + target.getUsername() + ").");
        });
    }

    /**
     * Permanent removal. Accounts with recorded sales can never be deleted
     * (financial history must stay intact) — deactivate those instead. The
     * account's own activity-trail rows are purged in the same transaction so
     * foreign keys stay satisfied; the deletion itself is logged under the
     * Owner's id.
     */
    public void deleteUser(User actor, int userId) {
        AccessControl.require(actor, User.ROLE_OWNER);
        User target = find(userId);
        if (target.getUserId() == actor.getUserId()) {
            throw new ServiceException("You cannot delete your own account.");
        }
        if (User.ROLE_OWNER.equals(target.getRole()) && countOwners() <= 1) {
            throw new ServiceException("You cannot delete the last remaining Owner account.");
        }
        if (userDAO.hasSales(userId)) {
            throw new ServiceException("Account #" + userId + " has recorded sales and cannot be deleted. Deactivate it instead.");
        }
        Transaction.run(c -> {
            logService.purgeUser(c, userId);
            userDAO.delete(c, userId);
            logService.log(c, actor.getUserId(), "USER_DELETE",
                    "Deleted account #" + userId + " (" + target.getUsername() + ", " + target.getRole() + ").");
        });
    }

    public void resetPassword(User actor, int userId, String newPassword) {
        AccessControl.require(actor, User.ROLE_OWNER);
        find(userId);
        validatePassword(newPassword);
        String hash = PasswordUtil.hash(newPassword);
        Transaction.run(c -> {
            userDAO.updatePassword(c, userId, hash);
            logService.log(c, actor.getUserId(), "USER_PASSWORD_RESET", "Reset password for account #" + userId + ".");
        });
    }

    // ---------------------------------------------------------------- helpers

    private User find(int userId) {
        return userDAO.findById(userId).orElseThrow(() -> new ServiceException("User #" + userId + " not found."));
    }

    private long countOwners() {
        return userDAO.findAll().stream().filter(u -> User.ROLE_OWNER.equals(u.getRole())).count();
    }

    private static String required(String v, String field) {
        if (v == null || v.trim().isEmpty()) throw new ServiceException(field + " is required.");
        return v.trim();
    }

    private static void validatePassword(String p) {
        if (p == null || p.length() < MIN_PASSWORD) {
            throw new ServiceException("Password must be at least " + MIN_PASSWORD + " characters long.");
        }
    }

    private static void validateRole(String role) {
        if (role == null || !Arrays.asList(User.ROLES).contains(role)) throw new ServiceException("Invalid role.");
    }
}
