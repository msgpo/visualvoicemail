package au.com.wallaceit.voicemail.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Stack;
import java.util.Set;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import au.com.wallaceit.voicemail.search.SearchSpecification.Attribute;
import au.com.wallaceit.voicemail.search.SearchSpecification.SearchCondition;
import au.com.wallaceit.voicemail.search.SearchSpecification.SearchField;


/**
 * This class stores search conditions. It's basically a boolean expression binary tree.
 * The output will be SQL queries ( obtained by traversing inorder ).
 *
 * TODO removing conditions from the tree
 * TODO implement NOT as a node again
 */
public class ConditionsTreeNode implements Parcelable {

    public enum Operator {
        AND, OR, CONDITION;
    }

    public au.com.wallaceit.voicemail.search.ConditionsTreeNode mLeft;
    public au.com.wallaceit.voicemail.search.ConditionsTreeNode mRight;
    public au.com.wallaceit.voicemail.search.ConditionsTreeNode mParent;

    /*
     * If mValue isn't CONDITION then mCondition contains a real
     * condition, otherwise it's null.
     */
    public Operator mValue;
    public SearchCondition mCondition;

    /*
     * Used for storing and retrieving the tree to/from the database.
     * The algorithm is called "modified preorder tree traversal".
     */
    public int mLeftMPTTMarker;
    public int mRightMPTTMarker;


    ///////////////////////////////////////////////////////////////
    // Static Helpers to restore a tree from a database cursor
    ///////////////////////////////////////////////////////////////
    /**
     * Builds a condition tree starting from a database cursor. The cursor
     * should point to rows representing the nodes of the tree.
     *
     * @param cursor Cursor pointing to the first of a bunch or rows. Each rows
     *  should contains 1 tree node.
     * @return A condition tree.
     */
    public static au.com.wallaceit.voicemail.search.ConditionsTreeNode buildTreeFromDB(Cursor cursor) {
        Stack<au.com.wallaceit.voicemail.search.ConditionsTreeNode> stack = new Stack<au.com.wallaceit.voicemail.search.ConditionsTreeNode>();
        au.com.wallaceit.voicemail.search.ConditionsTreeNode tmp = null;

        // root node
        if (cursor.moveToFirst()) {
            tmp = buildNodeFromRow(cursor);
            stack.push(tmp);
        }

        // other nodes
        while (cursor.moveToNext()) {
            tmp = buildNodeFromRow(cursor);
            if (tmp.mRightMPTTMarker < stack.peek().mRightMPTTMarker) {
                stack.peek().mLeft = tmp;
                stack.push(tmp);
            } else {
                while (stack.peek().mRightMPTTMarker < tmp.mRightMPTTMarker) {
                    stack.pop();
                }
                stack.peek().mRight = tmp;
            }
        }
        return tmp;
    }

    /**
     * Converts a single database row to a single condition node.
     *
     * @param cursor Cursor pointing to the row we want to convert.
     * @return A single ConditionsTreeNode
     */
    private static au.com.wallaceit.voicemail.search.ConditionsTreeNode buildNodeFromRow(Cursor cursor) {
        au.com.wallaceit.voicemail.search.ConditionsTreeNode result = null;
        SearchCondition condition = null;

        Operator tmpValue = au.com.wallaceit.voicemail.search.ConditionsTreeNode.Operator.valueOf(cursor.getString(5));

        if (tmpValue == Operator.CONDITION) {
            condition = new SearchCondition(SearchField.valueOf(cursor.getString(0)),
                    Attribute.valueOf(cursor.getString(2)), cursor.getString(1));
        }

        result = new au.com.wallaceit.voicemail.search.ConditionsTreeNode(condition);
        result.mValue = tmpValue;
        result.mLeftMPTTMarker = cursor.getInt(3);
        result.mRightMPTTMarker = cursor.getInt(4);

        return result;
    }


    ///////////////////////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////////////////////
    public ConditionsTreeNode(SearchCondition condition) {
        mParent = null;
        mCondition = condition;
        mValue = Operator.CONDITION;
    }

    public ConditionsTreeNode(au.com.wallaceit.voicemail.search.ConditionsTreeNode parent, Operator op) {
        mParent = parent;
        mValue = op;
        mCondition = null;
    }


    /* package */ au.com.wallaceit.voicemail.search.ConditionsTreeNode cloneTree() {
        if (mParent != null) {
            throw new IllegalStateException("Can't call cloneTree() for a non-root node");
        }

        au.com.wallaceit.voicemail.search.ConditionsTreeNode copy = new au.com.wallaceit.voicemail.search.ConditionsTreeNode(mCondition.clone());

        copy.mLeftMPTTMarker = mLeftMPTTMarker;
        copy.mRightMPTTMarker = mRightMPTTMarker;

        copy.mLeft = (mLeft == null) ? null : mLeft.cloneNode(copy);
        copy.mRight = (mRight == null) ? null : mRight.cloneNode(copy);

        return copy;
    }

    private au.com.wallaceit.voicemail.search.ConditionsTreeNode cloneNode(au.com.wallaceit.voicemail.search.ConditionsTreeNode parent) {
        au.com.wallaceit.voicemail.search.ConditionsTreeNode copy = new au.com.wallaceit.voicemail.search.ConditionsTreeNode(parent, mValue);

        copy.mCondition = mCondition.clone();
        copy.mLeftMPTTMarker = mLeftMPTTMarker;
        copy.mRightMPTTMarker = mRightMPTTMarker;

        copy.mLeft = (mLeft == null) ? null : mLeft.cloneNode(copy);
        copy.mRight = (mRight == null) ? null : mRight.cloneNode(copy);

        return copy;
    }

    ///////////////////////////////////////////////////////////////
    // Public modifiers
    ///////////////////////////////////////////////////////////////
    /**
     * Adds the expression as the second argument of an AND
     * clause to this node.
     *
     * @param expr Expression to 'AND' with.
     * @return New top AND node.
     * @throws Exception
     */
    public au.com.wallaceit.voicemail.search.ConditionsTreeNode and(au.com.wallaceit.voicemail.search.ConditionsTreeNode expr) throws Exception {
        return add(expr, Operator.AND);
    }

    /**
     * Convenience method.
     * Adds the provided condition as the second argument of an AND
     * clause to this node.
     *
     * @param condition Condition to 'AND' with.
     * @return New top AND node, new root.
     */
    public au.com.wallaceit.voicemail.search.ConditionsTreeNode and(SearchCondition condition) {
        try {
            au.com.wallaceit.voicemail.search.ConditionsTreeNode tmp = new au.com.wallaceit.voicemail.search.ConditionsTreeNode(condition);
            return and(tmp);
        } catch (Exception e) {
            // impossible
            return null;
        }
    }

    /**
     * Adds the expression as the second argument of an OR
     * clause to this node.
     *
     * @param expr Expression to 'OR' with.
     * @return New top OR node.
     * @throws Exception
     */
    public au.com.wallaceit.voicemail.search.ConditionsTreeNode or(au.com.wallaceit.voicemail.search.ConditionsTreeNode expr) throws Exception {
        return add(expr, Operator.OR);
    }

    /**
     * Convenience method.
     * Adds the provided condition as the second argument of an OR
     * clause to this node.
     *
     * @param condition Condition to 'OR' with.
     * @return New top OR node, new root.
     */
    public au.com.wallaceit.voicemail.search.ConditionsTreeNode or(SearchCondition condition) {
        try {
            au.com.wallaceit.voicemail.search.ConditionsTreeNode tmp = new au.com.wallaceit.voicemail.search.ConditionsTreeNode(condition);
            return or(tmp);
        } catch (Exception e) {
            // impossible
            return null;
        }
    }

    /**
     * This applies the MPTT labeling to the subtree of which this node
     * is the root node.
     *
     * For a description on MPTT see:
     * http://www.sitepoint.com/hierarchical-data-database-2/
     */
    public void applyMPTTLabel() {
        applyMPTTLabel(1);
    }


    ///////////////////////////////////////////////////////////////
    // Public accessors
    ///////////////////////////////////////////////////////////////
    /**
     * Returns the condition stored in this node.
     * @return Condition stored in the node.
     */
    public SearchCondition getCondition() {
        return mCondition;
    }

    /**
     * Get a set of all the leaves in the tree.
     * @return Set of all the leaves.
     */
    public Set<au.com.wallaceit.voicemail.search.ConditionsTreeNode> getLeafSet() {
        Set<au.com.wallaceit.voicemail.search.ConditionsTreeNode> leafSet = new HashSet<au.com.wallaceit.voicemail.search.ConditionsTreeNode>();
        return getLeafSet(leafSet);
    }

    /**
     * Returns a list of all the nodes in the subtree of which this node
     * is the root. The list contains the nodes in a pre traversal order.
     *
     * @return List of all nodes in subtree in preorder.
     */
    public List<au.com.wallaceit.voicemail.search.ConditionsTreeNode> preorder() {
        List<au.com.wallaceit.voicemail.search.ConditionsTreeNode> result = new ArrayList<au.com.wallaceit.voicemail.search.ConditionsTreeNode>();
        Stack<au.com.wallaceit.voicemail.search.ConditionsTreeNode> stack = new Stack<au.com.wallaceit.voicemail.search.ConditionsTreeNode>();
        stack.push(this);

        while (!stack.isEmpty()) {
            au.com.wallaceit.voicemail.search.ConditionsTreeNode current = stack.pop();

            if (current.mLeft != null) {
                stack.push(current.mLeft);
            }

            if (current.mRight != null) {
                stack.push(current.mRight);
            }

            result.add(current);
        }

        return result;
    }


    ///////////////////////////////////////////////////////////////
    // Private class logic
    ///////////////////////////////////////////////////////////////
    /**
     * Adds two new ConditionTreeNodes, one for the operator and one for the
     * new condition. The current node will end up on the same level as the
     * one provided in the arguments, they will be siblings. Their common
     * parent node will be one containing the operator provided in the arguments.
     * The method will update all the required references so the tree ends up in
     * a valid state.
     *
     * This method only supports node arguments with a null parent node.
     *
     * @param Node to add.
     * @param Operator that will connect the new node with this one.
     * @return New parent node, containing the operator.
     * @throws Exception Throws when the provided new node does not have a null parent.
     */
    private au.com.wallaceit.voicemail.search.ConditionsTreeNode add(au.com.wallaceit.voicemail.search.ConditionsTreeNode node, Operator op) throws Exception {
        if (node.mParent != null) {
            throw new Exception("Can only add new expressions from root node down.");
        }

        au.com.wallaceit.voicemail.search.ConditionsTreeNode tmpNode = new au.com.wallaceit.voicemail.search.ConditionsTreeNode(mParent, op);
        tmpNode.mLeft = this;
        tmpNode.mRight = node;

        if (mParent != null) {
            mParent.updateChild(this, tmpNode);
        }

        this.mParent = tmpNode;
        node.mParent = tmpNode;

        return tmpNode;
    }

    /**
     * Helper method that replaces a child of the current node with a new node.
     * If the provided old child node was the left one, left will be replaced with
     * the new one. Same goes for the right one.
     *
     * @param oldChild Old child node to be replaced.
     * @param newChild New child node.
     */
    private void updateChild(au.com.wallaceit.voicemail.search.ConditionsTreeNode oldChild, au.com.wallaceit.voicemail.search.ConditionsTreeNode newChild) {
        // we can compare objects id's because this is the desired behaviour in this case
        if (mLeft == oldChild) {
            mLeft = newChild;
        } else if (mRight == oldChild) {
            mRight = newChild;
        }
    }

    /**
     * Recursive function to gather all the leaves in the subtree of which
     * this node is the root.
     *
     * @param leafSet Leafset that's being built.
     * @return Set of leaves being completed.
     */
    private Set<au.com.wallaceit.voicemail.search.ConditionsTreeNode> getLeafSet(Set<au.com.wallaceit.voicemail.search.ConditionsTreeNode> leafSet) {
        if (mLeft == null && mRight == null) {
            // if we ended up in a leaf, add ourself and return
            leafSet.add(this);
            return leafSet;
        }

        // we didn't end up in a leaf
        if (mLeft != null) {
            mLeft.getLeafSet(leafSet);
        }

        if (mRight != null) {
            mRight.getLeafSet(leafSet);
        }
        return leafSet;
    }

    /**
     * This applies the MPTT labeling to the subtree of which this node
     * is the root node.
     *
     * For a description on MPTT see:
     * http://www.sitepoint.com/hierarchical-data-database-2/
     */
    private int applyMPTTLabel(int label) {
        mLeftMPTTMarker = label;

        if (mLeft != null) {
            label = mLeft.applyMPTTLabel(label += 1);
        }

        if (mRight != null) {
            label = mRight.applyMPTTLabel(label += 1);
        }

        ++label;
        mRightMPTTMarker = label;
        return label;
    }


    ///////////////////////////////////////////////////////////////
    // Parcelable
    //
    // This whole class has to be parcelable because it's passed
    // on through intents.
    ///////////////////////////////////////////////////////////////
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mValue.ordinal());
        dest.writeParcelable(mCondition, flags);
        dest.writeParcelable(mLeft, flags);
        dest.writeParcelable(mRight, flags);
    }

    public static final Creator<au.com.wallaceit.voicemail.search.ConditionsTreeNode> CREATOR =
            new Creator<au.com.wallaceit.voicemail.search.ConditionsTreeNode>() {

        @Override
        public au.com.wallaceit.voicemail.search.ConditionsTreeNode createFromParcel(Parcel in) {
            return new au.com.wallaceit.voicemail.search.ConditionsTreeNode(in);
        }

        @Override
        public au.com.wallaceit.voicemail.search.ConditionsTreeNode[] newArray(int size) {
            return new au.com.wallaceit.voicemail.search.ConditionsTreeNode[size];
        }
    };

    private ConditionsTreeNode(Parcel in) {
        mValue = Operator.values()[in.readInt()];
        mCondition = in.readParcelable(au.com.wallaceit.voicemail.search.ConditionsTreeNode.class.getClassLoader());
        mLeft = in.readParcelable(au.com.wallaceit.voicemail.search.ConditionsTreeNode.class.getClassLoader());
        mRight = in.readParcelable(au.com.wallaceit.voicemail.search.ConditionsTreeNode.class.getClassLoader());
        mParent = null;

        if (mLeft != null) {
            mLeft.mParent = this;
        }

        if (mRight != null) {
            mRight.mParent = this;
        }
    }
}