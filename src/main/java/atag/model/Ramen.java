package atag.model;

import org.neo4j.graphdb.RelationshipType;

/**
 * The RAMEN domain meta-model: the vocabulary every project model refines.
 * <p>
 * RAMEN distinguishes four generic node types - a {@link Concept#COLLECTION} groups
 * material, {@link Concept#CONTENT} carries the text itself, {@link Concept#ENTITY}
 * denotes something the text refers to, and {@link Concept#ANNOTATION} is an editorial
 * statement about content - connected by three generic relation types. Concrete label
 * and relationship names are not fixed here; they are chosen by a {@link ProjectModel}.
 */
public final class Ramen {

    public enum Concept {
        COLLECTION,
        CONTENT,
        ENTITY,
        ANNOTATION
    }

    public static final RelationshipType PART_OF = RelationshipType.withName("PART_OF");
    public static final RelationshipType HAS_ANNOTATION = RelationshipType.withName("HAS_ANNOTATION");
    public static final RelationshipType REFERS_TO = RelationshipType.withName("REFERS_TO");

    private Ramen() {
    }
}
