package com.example.communitymember20;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Central place to resolve which users/{category} node a role belongs to.
 * Keeping this in one place means we change it once and it applies everywhere.
 */
public class UserNodeHelper {

    public static final String NODE_MEMBERS = "community_members";
    public static final String NODE_DRIVERS = "drivers";
    public static final String NODE_ADMINS  = "admins";

    /** Returns the node name for a given role string. */
    public static String nodeForRole(String role) {
        if (role == null) return NODE_MEMBERS;
        switch (role) {
            case "Driver": return NODE_DRIVERS;
            case "Admin":  return NODE_ADMINS;
            default:       return NODE_MEMBERS;
        }
    }

    /** Returns the role string for a given node name. */
    public static String roleForNode(String node) {
        if (node == null) return "Community Member";
        switch (node) {
            case NODE_DRIVERS: return "Driver";
            case NODE_ADMINS:  return "Admin";
            default:           return "Community Member";
        }
    }

    /** Returns a DatabaseReference pointing at users/{node}. */
    public static DatabaseReference usersNode(String roleOrNode) {
        // Accept either "Driver" or "drivers"
        String node;
        if (NODE_MEMBERS.equals(roleOrNode)
                || NODE_DRIVERS.equals(roleOrNode)
                || NODE_ADMINS.equals(roleOrNode)) {
            node = roleOrNode;
        } else {
            node = nodeForRole(roleOrNode);
        }
        return FirebaseDatabase.getInstance()
                .getReference("users")
                .child(node);
    }

    /** Returns a DatabaseReference pointing at users/{node}/{uid}. */
    public static DatabaseReference userRef(String roleOrNode, String uid) {
        return usersNode(roleOrNode).child(uid);
    }

    /** All three nodes as an array — useful when you need to search across all roles. */
    public static String[] allNodes() {
        return new String[]{ NODE_MEMBERS, NODE_DRIVERS, NODE_ADMINS };
    }
}