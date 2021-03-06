package org.batfish.datamodel.questions;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.regex.Pattern;
import org.batfish.common.BatfishException;
import org.batfish.datamodel.Interface;

/**
 * Enables specification of groups of interfaces in various questions.
 *
 * <p>Currently supported example specifiers:
 *
 * <ul>
 *   <li>Loopback.* —> all interfaces with matching names
 *   <li>desc:KK.* -> all interfaces whose description matches KK.*
 * </ul>
 *
 * <p>In the future, we might need other tags and boolean expressions over tags.
 */
public class InterfacesSpecifier {

  public enum Type {
    DESC,
    NAME
  }

  public static InterfacesSpecifier ALL = new InterfacesSpecifier(".*");

  private final String _expression;

  private final Pattern _regex;

  private final Type _type;

  public InterfacesSpecifier(String expression) {
    _expression = expression;

    String[] parts = expression.split(":");

    if (parts.length == 1) {
      _type = Type.NAME;
      _regex = Pattern.compile(_expression);
    } else if (parts.length == 2) {
      try {
        _type = Type.valueOf(parts[0].toUpperCase());
        _regex = Pattern.compile(parts[1]);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException(
            "Illegal InterfacesSpecifier filter " + parts[1] + ".  Should be 'name' or 'desc'");
      }
    } else {
      throw new IllegalArgumentException("Cannot parse InterfacesSpecifier " + expression);
    }
  }

  @JsonIgnore
  public Pattern getRegex() {
    return _regex;
  }

  @JsonIgnore
  public Type getType() {
    return _type;
  }

  public boolean matches(Interface iface) {
    switch (_type) {
      case DESC:
        return _regex.matcher(iface.getDescription()).matches();
      case NAME:
        return _regex.matcher(iface.getName()).matches();
      default:
        throw new BatfishException("Unhandled InterfacesSpecifier type: " + _type);
    }
  }

  @JsonValue
  public String toString() {
    return _expression;
  }
}
