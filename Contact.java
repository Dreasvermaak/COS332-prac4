import java.io.Serializable;

public class Contact implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String name;
    private String phone;
    private String cellPhone;
    private byte[] photo;
    
    public Contact(String name, String phone, String cellPhone) {
        this.name = name;
        this.phone = phone;
        this.cellPhone = cellPhone;
    }
    
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
    
    public void setPhoto(byte[] photo) {
        if (photo != null && photo.length > 0) {
            this.photo = new byte[photo.length];
            System.arraycopy(photo, 0, this.photo, 0, photo.length);
        } else {
            this.photo = null;
        }
    }
    
    public byte[] getPhoto() {
        if (photo != null) {
            byte[] copy = new byte[photo.length];
            System.arraycopy(photo, 0, copy, 0, photo.length);
            return copy;
        }
        return null;
    }
    
    public boolean hasPhoto() {
        return photo != null && photo.length > 0;
    }
    
    @Override
    public String toString() {
        return name + " (Phone: " + phone + ", Cell: " + cellPhone + ")";
    }
}