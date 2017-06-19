package objects;

import java.util.Vector;

/**
 * Created by 黄敏聪 on 2017/5/1.
 */
public class MultiChatInfo implements java.io.Serializable {
    private int id;
    private Vector<String> people;

    public MultiChatInfo(int id, Vector<String> people) {
        this.id = id;
        this.people = people;
    }

    public int getId() {
        return id;
    }

    public Vector<String> getPeople() {
        return people;
    }
}
