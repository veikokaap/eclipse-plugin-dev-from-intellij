package ee.veikokaap.base.util

import java.awt.GridBagConstraints
import java.awt.Insets

fun gridBagConstraints(
    gridx: Int = GridBagConstraints.RELATIVE,
    gridy: Int = GridBagConstraints.RELATIVE,
    gridwidth: Int = 1,
    gridheight: Int = 1,
    weightx: Double = 0.0,
    weighty: Double = 0.0,
    anchor: Int = GridBagConstraints.CENTER,
    fill: Int = GridBagConstraints.NONE,
    insets: Insets = Insets(0, 0, 0, 0),
    ipadx: Int = 0,
    ipady: Int = 0
): GridBagConstraints {
  return GridBagConstraints(
      gridx, gridy,
      gridwidth, gridheight,
      weightx, weighty,
      anchor, fill,
      insets, ipadx, ipady
  )
}

fun GridBagConstraints.copy(
    gridx: Int = this.gridx,
    gridy: Int = this.gridy,
    gridwidth: Int = this.gridwidth,
    gridheight: Int = this.gridheight,
    weightx: Double = this.weightx,
    weighty: Double = this.weighty,
    anchor: Int = this.anchor,
    fill: Int = this.fill,
    insets: Insets = this.insets,
    ipadx: Int = this.ipadx,
    ipady: Int = this.ipady
): GridBagConstraints {
  return GridBagConstraints(
      gridx, gridy,
      gridwidth, gridheight,
      weightx, weighty,
      anchor, fill,
      insets, ipadx, ipady
  )
}

fun GridBagConstraints.nextRow(): GridBagConstraints {
  this.gridy++
  return this
}

fun GridBagConstraints.nextColumn(): GridBagConstraints {
  this.gridx++
  return this
}

fun GridBagConstraints.fillHorizontally(weightx: Double): GridBagConstraints {
  this.weightx = weightx
  if (this.fill == GridBagConstraints.VERTICAL) {
    this.fill = GridBagConstraints.BOTH
  }
  else if (this.fill == GridBagConstraints.NONE) {
    this.fill = GridBagConstraints.HORIZONTAL
  }
  return this
}

fun GridBagConstraints.staticHorizontally(): GridBagConstraints {
  weightx = 0.0
  if (this.fill == GridBagConstraints.BOTH) {
    this.fill = GridBagConstraints.VERTICAL
  }
  else if (this.fill == GridBagConstraints.HORIZONTAL) {
    this.fill = GridBagConstraints.NONE
  }
  return this
}

fun GridBagConstraints.fillVertically(weighty: Double): GridBagConstraints {
  this.weighty = weighty
  if (this.fill == GridBagConstraints.HORIZONTAL) {
    this.fill = GridBagConstraints.BOTH
  }
  else if (this.fill == GridBagConstraints.NONE) {
    this.fill = GridBagConstraints.VERTICAL
  }
  return this
}

fun GridBagConstraints.staticVertically(): GridBagConstraints {
  weighty = 0.0
  if (this.fill == GridBagConstraints.BOTH) {
    this.fill = GridBagConstraints.HORIZONTAL
  }
  else if (this.fill == GridBagConstraints.VERTICAL) {
    this.fill = GridBagConstraints.NONE
  }
  return this
}

fun GridBagConstraints.fillBoth(weightx: Double, weighty: Double): GridBagConstraints {
  return this.fillHorizontally(weightx).fillVertically(weighty)
}

fun GridBagConstraints.fillNone(): GridBagConstraints {
  return this.staticHorizontally().staticVertically()
}

fun GridBagConstraints.padded(ipadx: Int, ipady: Int): GridBagConstraints {
  this.ipadx = ipadx
  this.ipady = ipady
  return this
}

fun GridBagConstraints.withInsets(insets: Insets): GridBagConstraints {
  this.insets = insets
  return this
}
