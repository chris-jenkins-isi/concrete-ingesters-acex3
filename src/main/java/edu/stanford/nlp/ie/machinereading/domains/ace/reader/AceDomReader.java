package edu.stanford.nlp.ie.machinereading.domains.ace.reader;

import edu.stanford.nlp.ie.machinereading.common.DomReader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * DOM reader for an ACE specification.
 *
 * @author David McClosky
 */
public class AceDomReader extends DomReader {

    private static AceCharSeq parseCharSeq(Node node) {
        Node child = getChildByName(node, "charseq");
        String start = getAttributeValue(child, "START");
        String end = getAttributeValue(child, "END");
        String text = child.getFirstChild().getNodeValue();
        return new AceCharSeq(text,
                Integer.parseInt(start),
                Integer.parseInt(end));
    }

    /**
     * Extracts one entity mention
     */
    private static AceEntityMention parseEntityMention(Node node) {
        String id = getAttributeValue(node, "ID");
        String type = getAttributeValue(node, "TYPE");
        String ldctype = getAttributeValue(node, "LDCTYPE");
        AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
        AceCharSeq head = parseCharSeq(getChildByName(node, "head"));
        return (new AceEntityMention(id, type, ldctype, extent, head));
    }

    /**
     * Extracts one timex2 mention
     */
    private static AceEntityMention parseTimex2Mention(Node node) {
        String id = getAttributeValue(node, "ID");
        String type = "TIM";
        AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
        AceCharSeq head = parseCharSeq(getChildByName(node, "extent"));
        return (new AceEntityMention(id, type, type, extent, head));
    }

    /**
     * Extracts one value mention
     */
    private static AceEntityMention parseValueMention(Node node, String type) {
        String id = getAttributeValue(node, "ID");
        AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
        AceCharSeq head = parseCharSeq(getChildByName(node, "extent"));
        return (new AceEntityMention(id, type, type, extent, head));
    }

    /**
     * Extracts a trigger as an entity mention
     */
    private static AceEntityMention parseTriggerAsEM(Node node) {
        String id = getAttributeValue(node, "ID") + "-TRIGGER-0";
        String type = "TRIGGER";
        String ldctype = "TRIGGER";
        AceCharSeq extent = parseCharSeq(getChildByName(node, "anchor"));
        AceCharSeq head = parseCharSeq(getChildByName(node, "anchor"));
        return (new AceEntityMention(id, type, ldctype, extent, head));
    }

    /**
     * Extracts info about one relation mention
     */
    private static AceRelationMention parseRelationMention(Node node,
                                                           AceDocument doc) {
        String id = getAttributeValue(node, "ID");
        AceCharSeq extent = parseCharSeq(getChildByName(node, "extent"));
        String lc = getAttributeValue(node, "LEXICALCONDITION");

        // create the mention
        AceRelationMention mention = new AceRelationMention(id, extent, lc);

        // find the mention args
        List<Node> args = getChildrenByName(node, "relation_mention_argument");
        for (Node arg : args) {
            String role = getAttributeValue(arg, "ROLE");
            String refid = getAttributeValue(arg, "REFID");
            AceEntityMention am = doc.getEntityMention(refid);

            if (am != null) {
                am.addRelationMention(mention);
                if (role.equalsIgnoreCase("arg-1")) {
                    mention.getArgs()[0] = new AceRelationMentionArgument(role, am);
                } else if (role.equalsIgnoreCase("arg-2")) {
                    mention.getArgs()[1] = new AceRelationMentionArgument(role, am);
                } else {
                    throw new RuntimeException("Invalid relation mention argument role: " + role);
                }
            }
        }

        return mention;
    }

    /**
     * Extracts info about one relation mention
     */
    private static AceEventMention parseEventMention(Node node,
                                                     AceDocument doc) {
        String id = getAttributeValue(node, "ID");
        // change field extent to ldc_scope
        AceCharSeq extent = parseCharSeq(getChildByName(node, "ldc_scope"));
        AceCharSeq anchor = parseCharSeq(getChildByName(node, "anchor"));

        // create the mention
        AceEventMention mention = new AceEventMention(id, extent, anchor);

        // append trigger from entity
        AceEntityMention trigger = doc.getEntityMention(id + "-TRIGGER-0");
        if (trigger != null) {
            trigger.addEventMention(mention);
            mention.addArg(trigger, "TRIGGER");
        } else {
            System.out.println("Trigger not found for event-" + id);
        }

        // find the mention args
        List<Node> args = getChildrenByName(node, "event_mention_argument");
        for (Node arg : args) {
            String role = getAttributeValue(arg, "ROLE");
            String refid = getAttributeValue(arg, "REFID");
            AceEntityMention am = doc.getEntityMention(refid);

            if (am != null) {
                am.addEventMention(mention);
                mention.addArg(am, role);
            } else {
                System.out.println(refid);
            }
        }

        return mention;
    }

    /**
     * Parses one ACE specification
     *
     * @return Simply displays the events to stdout
     */
    public static AceDocument parseDocument(File f)
            throws IOException, SAXException, ParserConfigurationException {

        // parse the Dom document
        Document document = readDocument(f);

        //
        // create the ACE document object
        //
        Node docElement = document.getElementsByTagName("document").item(0);
        AceDocument aceDoc =
                new AceDocument(getAttributeValue(docElement, "DOCID"));

        //
        // read all entities
        //
        NodeList entities = document.getElementsByTagName("entity");
        for (int i = 0; i < entities.getLength(); i++) {
            Node node = entities.item(i);

            //
            // the entity type and subtype
            //
            String id = getAttributeValue(node, "ID");
            String type = getAttributeValue(node, "TYPE");
            String subtype = getAttributeValue(node, "SUBTYPE");
            String cls = getAttributeValue(node, "CLASS");

            // create the entity
            AceEntity entity = new AceEntity(id, type, subtype, cls);
            aceDoc.addEntity(entity);

            // fetch all mentions of this entity
            List<Node> mentions = getChildrenByName(node, "entity_mention");

            // parse all its mentions
            for (Node mention1 : mentions) {
                AceEntityMention mention = parseEntityMention(mention1);
                entity.addMention(mention);
                aceDoc.addEntityMention(mention);
            }
        }

        //
        // read all times
        //
        NodeList times = document.getElementsByTagName("timex2");
        for (int i = 0; i < times.getLength(); i++) {
            Node node = times.item(i);

            //
            // the entity type and subtype
            //
            String id = getAttributeValue(node, "ID");
            String type = "TIM";
            String subtype = "time";
            String cls = "TIM";

            // create the entity
            AceEntity entity = new AceEntity(id, type, subtype, cls);
            aceDoc.addEntity(entity);

            // fetch all mentions of this entity
            List<Node> mentions = getChildrenByName(node, "timex2_mention");

            // parse all its mentions
            for (Node mention1 : mentions) {
                AceEntityMention mention = parseTimex2Mention(mention1);
                entity.addMention(mention);
                aceDoc.addEntityMention(mention);
            }
        }


        //
        // read all values
        //
        NodeList values = document.getElementsByTagName("value");
        for (int i = 0; i < values.getLength(); i++) {
            Node node = values.item(i);

            //
            // the entity type and subtype
            //
            String id = getAttributeValue(node, "ID");
            String type = getAttributeValue(node, "TYPE");

            if (type.equals("Numeric")) {
                type = "NUM";
            } else if (type.equals("Contact-Info")) {
                type = "CTI";
            } else if (type.equals("Crime")) {
                type = "CRM";
            } else if (type.equals("Job-Title")) {
                type = "JOB";
            } else if (type.equals("Sentence")) {
                type = "SEN";
            }

            String subtype = type;
            if (type.equals("NUM") || type.equals("CTI")) {
                subtype = getAttributeValue(node, "SUBTYPE");
            }

            String cls = type;

            // create the entity
            AceEntity entity = new AceEntity(id, type, subtype, cls);
            aceDoc.addEntity(entity);

            // fetch all mentions of this entity
            List<Node> mentions = getChildrenByName(node, "value_mention");

            // parse all its mentions
            for (Node mention1 : mentions) {
                AceEntityMention mention = parseValueMention(mention1, type);
                entity.addMention(mention);
                aceDoc.addEntityMention(mention);
            }
        }

        //
        // read all event triggers as entities and append them into arguments
        //
        NodeList triggers = document.getElementsByTagName("event_mention");
        for (int i = 0; i < triggers.getLength(); i++) {
            Node node = triggers.item(i);

            //
            // the entity type and subtype
            //
            String id = getAttributeValue(node, "ID") + "-TRIGGER";
            String type = "TRIGGER";
            String subtype = "TRIGGER";
            String cls = "TRIGGER";

            // create the entity
            AceEntity trigger = new AceEntity(id, type, subtype, cls);
            aceDoc.addEntity(trigger);

            AceEntityMention mention = parseTriggerAsEM(node);
            trigger.addMention(mention);
            aceDoc.addEntityMention(mention);
        }

        //
        // read all events
        //
        NodeList events = document.getElementsByTagName("event");
        for (int i = 0; i < events.getLength(); i++) {
            Node node = events.item(i);

            //
            // the event type, subtype, tense, and modality
            //
            String id = getAttributeValue(node, "ID");
            String type = getAttributeValue(node, "TYPE");
            String subtype = getAttributeValue(node, "SUBTYPE");
            String modality = getAttributeValue(node, "MODALITY");
            String polarity = getAttributeValue(node, "POLARITY");
            String genericity = getAttributeValue(node, "GENERICITY");
            String tense = getAttributeValue(node, "TENSE");

            // create the event
            AceEvent event = new AceEvent(id, type, subtype,
                    modality, polarity, genericity, tense);
            aceDoc.addEvent(event);

            // fetch all mentions of this relation
            List<Node> mentions = getChildrenByName(node, "event_mention");

            // traverse all mentions
            for (Node mention1 : mentions) {
                AceEventMention mention = parseEventMention(mention1, aceDoc);
                event.addMention(mention);
                aceDoc.addEventMention(mention);
            }
        }

        return aceDoc;
    }

    public static void main(String[] argv) throws Exception {
        argv = new String[1];
        argv[0] = "/Users/d22admin/USCGDrive/ISI/EventExtraction/5Algorithms/DataPreparation/concrete-ingesters-acex3/src/main/java/edu/bit/nlp/concrete/ingesters/acex3/CNN_LE_20030504.1200.01.apf.xml";
        if (argv.length != 1) {
            System.err.println("Usage: java AceDomReader <APF file>");
            System.exit(1);
        }

        File f = new File(argv[0]);
        AceDocument doc = parseDocument(f);
        System.out.println(doc.getSentences());
        System.out.println(doc.getSentenceCount());


        System.out.println("Processed ACE document:\n" + doc);
        ArrayList<ArrayList<AceRelationMention>> r = doc.getAllRelationMentions();
        System.out.println(r);
        System.out.println("****************************************************************\n");
        Map<String, AceEntityMention> entities = doc.getEntityMentions();

        //System.out.println(entities);

        Map<String, AceEventMention> events = doc.getEventMentions();

        for (Map.Entry<String, AceEventMention> entry : events.entrySet()) {
            System.out.println(entry.getKey() + " anchor:" +entry.getValue().getAnchor()+
                    " type:" +entry.getValue().getParent().getType()+ "\n");
        }
        //System.out.println(events);
        System.out.println("size: " + r.size());
    }
}

