package io.descoped.stride.application;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class JakartaXmlTest {

    @Disabled
    @Test
    void xml() throws JAXBException {
        Book book = new Book();
        book.setId(1L);
        book.setName("Book1");
        book.setAuthor("Author1");

        JAXBContext context = JAXBContext.newInstance(Book.class);
        Marshaller mar = context.createMarshaller();
        mar.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        mar.marshal(book, System.out);
    }

    @XmlRootElement(name = "book")
    @XmlType(propOrder = {"id", "name", "author"})
    public static class Book {
        private Long id;
        private String name;
        private String author;

        @XmlAttribute
        public void setId(Long id) {
            this.id = id;
        }

        @XmlElement(name = "title")
        public void setName(String name) {
            this.name = name;
        }

        @XmlElement(name = "author")
        public void setAuthor(String author) {
            this.author = author;
        }

        // constructor, getters and setters


        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getAuthor() {
            return author;
        }
    }


}
