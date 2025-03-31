import java.io.Serializable;

public class Contact implements Serializable {
    private static final long serialVersionUID = 1L; // needed for serialization stuff
    
    // contact info
    private String name;
    private String phone;
    private String cellPhone;
    private byte[] photo; // store photo as bytes
    
    // constructor
    public Contact(String name, String phone, String cellPhone) {
        this.name = name;
        this.phone = phone;
        this.cellPhone = cellPhone;
    }
    
    // getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone;
    }
    
    public String getCellPhone() {
        return cellPhone;
    }
    
    public void setCellPhone(String cellPhone) {
        this.cellPhone = cellPhone;
    }
    
    // make a copy of the photo to avoid someone changing it directly
    public void setPhoto(byte[] photo) {
        if (photo != null && photo.length > 0) {
            this.photo = new byte[photo.length];
            System.arraycopy(photo, 0, this.photo, 0, photo.length); // copy the array
        } else {
            this.photo = null;
        }
    }
    
    public byte[] getPhoto() {
        if (photo != null) {
            byte[] copy = new byte[photo.length];
            System.arraycopy(photo, 0, copy, 0, photo.length); // return a copy
            return copy;
        }
        return null;
    }
    
    // check if we have a photo
    public boolean hasPhoto() {
        return photo != null && photo.length > 0;
    }
    
    @Override
    public String toString() {
        return name + " (Phone: " + phone + ", Cell: " + cellPhone + ")";
    }
}